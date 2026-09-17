package smtsched.bench;

import com.microsoft.z3.*;
import smtsched.encode.Encoder;
import smtsched.encode.XmtEncoder;
import smtsched.ground.Grounder;
import smtsched.schedule.Scheduler;
import smtsched.spec.Spec;

/**
 * 
 * Benchmark
 * Runs a single SMT solve for one {@link Spec} and reports the outcome
 * 
 * Each call creates its own Z3 {@link Context}, thus runs are fully isolated from eachtoher (no shared solver state)
 */
public final class Benchmark {
    private Benchmark() {}

    /**
     * Encode {@code spec} and asks Z3 to solve it once.
     * @param spec the task set and core count
     * @param symmetry whether to apply symmetry-breaking
     * @param source Label identifying where this spec came from (for CSV purposes only)
     * @param density The chance that any earlier task becomes this task's dependency
     * @param seed Seed used to generate the spec
     * @param timeoutMs Z3 solver timeout in milliseconds
     */
    public static BenchResult run(Spec spec, boolean symmetry, String source, double density, long seed, int timeoutMs) {
        try (Context ctx = new Context()) {
            Solver z3 = ctx.mkSolver();

            Params p = ctx.mkParams();
            p.add("timeout", timeoutMs);
            z3.setParameters(p);

            Encoder encoder = new Encoder(ctx, spec, symmetry);
            encoder.assertInto(z3);

            int assertions = z3.getAssertions().length;
            long t0 = System.nanoTime();
            Status status = z3.check();
            long ms = (System.nanoTime() - t0) / 1000000;

            return new BenchResult(source, "hand-ground", spec.tasks().size(), spec.processors(), density, seed, symmetry, status.toString(), ms, assertions);
        }
    }

    public static BenchResult runGrounded(Spec spec, boolean symmetry, String source, double density, long seed, int timeoutMs, Grounder grounder) {
        XmtEncoder encoder = new XmtEncoder(spec, symmetry);
        String grounded = grounder.ground(encoder.encode());

        try (Context ctx = new Context()) {
            Solver z3 = ctx.mkSolver();

            Params p = ctx.mkParams();
            p.add("timeout", timeoutMs);
            z3.setParameters(p);

            z3.add(ctx.parseSMTLIB2String(grounded, null, null, null, null));

            int assertions = z3.getAssertions().length;
            long t0 = System.nanoTime();
            Status status = z3.check();
            long ms = (System.nanoTime() - t0) / 1000000;

            return new BenchResult(source, "grounded", spec.tasks().size(), spec.processors(), density, seed, symmetry, status.toString(), ms, assertions);
        }
    }

    /** Greedy-permutation slice 0, random restarts after — always uses symmetry breaking, so no symmetry param. */
    public static BenchResult runHybridRestarts(Spec spec, String source, double density, long seed) {
        Scheduler scheduler = new Scheduler();

        long t0 = System.nanoTime();
        Scheduler.Result result = scheduler.solveWithHybridRestarts(spec);
        long ms = (System.nanoTime() - t0) / 1000000;

        String status = switch (result.outcome()) {
            case SCHEDULABLE -> "SATISFIABLE";
            case UNSCHEDULABLE -> "UNSATISFIABLE";
            case TIMEOUT -> "UNKNOWN";
        };

        // assertions not tracked here — Scheduler.Result doesn't expose the solver
        return new BenchResult(source, "restart-hybrid", spec.tasks().size(), spec.processors(), density, seed, true, status, ms, -1);
    }
}