package smtsched.schedule;

import com.microsoft.z3.*;

import smtsched.encode.CorePermutation;
import smtsched.encode.Encoder;
import smtsched.encode.Symmetry;
import smtsched.encode.TextEncoder;
import smtsched.encode.XmtEncoder;
import smtsched.ground.Grounder;
import smtsched.spec.Spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Runs an encoding through z3.
 *
 *   solve: baseline with eager grounder
 *   solveText: quantified SMT-LIB text, optionally grounded first.
 *   solveLateness: used when schedule is UNSAT
 */
public class Scheduler {

    public static final int TIMEOUT_MS = 10000;
    private static final int RESTART_MAX_SLICES = 4;
    public static final int RESTART_SLICE_MS = TIMEOUT_MS / RESTART_MAX_SLICES;

    public enum Outcome { SCHEDULABLE, UNSCHEDULABLE, TIMEOUT }

    public record Result(Outcome outcome, List<Encoder.Assignment> assignments) {
        static Result unsolved(Outcome outcome) { return new Result(outcome, List.of()); }
    }

    public record LateResult(boolean solved, List<Encoder.LateAssignment> assignments) {}

    /** Baseline: object encoder, deadlines hard. */
    public Result solve(Spec spec, Symmetry symmetry) {
        try (Context ctx = new Context()) {
            Solver z3 = newSolver(ctx);
            Encoder encoder = new Encoder(ctx, spec, symmetry);
            encoder.assertInto(z3);

            Status status = z3.check();
            return status == Status.SATISFIABLE
                    ? new Result(Outcome.SCHEDULABLE, encoder.decode(z3.getModel()))
                    : Result.unsolved(outcomeOf(status));
        }
    }

    /** Every slice picks an independent random permutation. */
    public Result solveWithRestarts(Spec spec) {
        return solveWithRestarts(spec, false);
    }

    /** Slice 0 uses the greedy-derived permutation (Approach 1's best guess); later slices fall back to random. */
    public Result solveWithHybridRestarts(Spec spec) {
        return solveWithRestarts(spec, true);
    }

    private Result solveWithRestarts(Spec spec, boolean greedyFirst) {
        try (Context ctx = new Context()) {
            Encoder encoder = new Encoder(ctx, spec, Symmetry.NONE); // symmetry applied manually per slice below
            Solver z3 = ctx.mkSolver();

            Params sliceParams = ctx.mkParams();
            sliceParams.add("timeout", RESTART_SLICE_MS);
            z3.setParameters(sliceParams);

            encoder.assertBaseInto(z3);

            Random rng = new Random();
            for (int slice = 0; slice < RESTART_MAX_SLICES; slice++) {
                z3.push();
                int[] order = (greedyFirst && slice == 0)
                        ? CorePermutation.fromGreedySchedule(spec)
                        : CorePermutation.shuffled(spec, rng);
                encoder.assertCoreSymmetryInto(z3, order);

                Status status = z3.check();
                if (status == Status.SATISFIABLE) {
                    return new Result(Outcome.SCHEDULABLE, encoder.decode(z3.getModel()));
                }
                if (status == Status.UNSATISFIABLE) {
                    return Result.unsolved(Outcome.UNSCHEDULABLE);
                }
                z3.pop(); // if UNKNOWN, discard this slice's symmetry constraints, try a new permutation
            }
            return Result.unsolved(Outcome.TIMEOUT);
        }
    }

    /**
     * Text path: emit quantified SMT-LIB, ground it, parse it back into z3.
     * Pass NoGround to hand the quantifiers straight to z3 (its own E-matching);
     * pass RustGrounder to have xmt-lib expand them first.
     */
    public Result solveText(Spec spec, Symmetry symmetry, Grounder grounder) {
        TextEncoder encoder = new TextEncoder(spec, symmetry);
        String grounded = grounder.ground(encoder.encode());

        try (Context ctx = new Context()) {
            Solver z3 = newSolver(ctx);
            z3.add(ctx.parseSMTLIB2String(grounded, null, null, null, null));

            Status status = z3.check();
            return status == Status.SATISFIABLE
                    ? new Result(Outcome.SCHEDULABLE, decodeText(ctx, z3.getModel(), encoder, spec))
                    : Result.unsolved(outcomeOf(status));
        }
    }

    public Result solveGrounded(Spec spec, Symmetry symmetry, Grounder grounder) {
        XmtEncoder encoder = new XmtEncoder(spec, symmetry);
        String grounded = stripUnusedDeclarations(grounder.ground(encoder.encode()));

        try (Context ctx = new Context()) {
            Solver z3 = newSolver(ctx);
            z3.add(ctx.parseSMTLIB2String(grounded, null, null, null, null));

            Status status = z3.check();
            return status == Status.SATISFIABLE
                ? new Result(Outcome.SCHEDULABLE, decodeText(ctx, z3.getModel(), encoder, spec))
                : Result.unsolved(outcomeOf(status));
        }
    }

    /** Soft deadlines: focuses on minimizing amount late under the event that schedulability is not feasible */
    public LateResult solveLateness(Spec spec, boolean symmetryBreaking) {
        try (Context ctx = new Context()) {
            Optimize opt = ctx.mkOptimize();
            opt.setParameters(timeout(ctx));

            Encoder encoder = new Encoder(ctx, spec, symmetryBreaking ? Symmetry.GREEDY : Symmetry.NONE);
            encoder.assertLatenessInto(opt);

            return opt.Check() == Status.SATISFIABLE
                    ? new LateResult(true, encoder.decodeLateness(opt.getModel()))
                    : new LateResult(false, List.of());
        }
    }


    private Solver newSolver(Context ctx) {
        Solver z3 = ctx.mkSolver();
        z3.setParameters(timeout(ctx));
        return z3;
    }

    private Params timeout(Context ctx) {
        Params p = ctx.mkParams();
        p.add("timeout", TIMEOUT_MS);
        return p;
    }

    /** UNKNOWN means the budget ran out — not that the problem is infeasible. */
    private Outcome outcomeOf(Status status) {
        return status == Status.UNSATISFIABLE ? Outcome.UNSCHEDULABLE : Outcome.TIMEOUT;
    }

    /**
     * Decode a model built from parsed text. We hold no IntExpr handles here (z3
     * created the symbols from the string), so rebuild the function declarations by
     * name, evaluate them at each task index, and map the index back to a task name.
     */
    private List<Encoder.Assignment> decodeText(Context ctx, Model model, TextEncoder encoder, Spec spec) {
        IntSort intSort = ctx.getIntSort();
        FuncDecl<IntSort> start = ctx.mkFuncDecl("start", new Sort[]{ intSort }, intSort);
        FuncDecl<IntSort> proc = ctx.mkFuncDecl("proc", new Sort[]{ intSort }, intSort);

        List<Encoder.Assignment> out = new ArrayList<>();
        for (int i = 0; i < encoder.taskCount(); i++) {
            long s = ((IntNum) model.evaluate(start.apply(ctx.mkInt(i)), true)).getInt64();
            int c = (int) ((IntNum) model.evaluate(proc.apply(ctx.mkInt(i)), true)).getInt64();
            long dur = spec.tasks().get(i).duration();

            out.add(new Encoder.Assignment(encoder.taskName(i), c, s, s + dur));
        }
        return out;
    }

    private List<Encoder.Assignment> decodeText(Context ctx, Model model, XmtEncoder encoder, Spec spec) {
        IntSort intSort = ctx.getIntSort();
        FuncDecl<IntSort> start = ctx.mkFuncDecl("start", new Sort[]{ intSort }, intSort);
        FuncDecl<IntSort> proc = ctx.mkFuncDecl("proc",  new Sort[]{ intSort }, intSort);

        List<Encoder.Assignment> out = new ArrayList<>();
        for (int i = 0; i < encoder.taskCount(); i++) {
            long s = ((IntNum) model.evaluate(start.apply(ctx.mkInt(i)), true)).getInt64();
            int  c = (int)  ((IntNum) model.evaluate(proc.apply(ctx.mkInt(i)), true)).getInt64();
            long dur = spec.tasks().get(i).duration();

            out.add(new Encoder.Assignment(encoder.taskName(i), c, s, s + dur));
        }
        return out;
    }

    public static String stripUnusedDeclarations(String smtlib) {
        return smtlib.lines()
                .filter(line -> !line.startsWith("(declare-fun Task") && !line.startsWith("(declare-fun Pair"))
                .reduce(new StringBuilder(), (sb, line) -> sb.append(line).append('\n'), StringBuilder::append)
                .toString();
    }

}