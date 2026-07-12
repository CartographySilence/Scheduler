package smtsched.encode;

import com.microsoft.z3.*;
import smtsched.spec.Spec;

import java.util.*;
import java.util.function.Consumer;

public class Encoder {

    public record Assignment(String task, int core, long start, long end) {}

    public record LateAssignment(String task, int core, long start, long end, long deadline, long late) {
        public boolean isLate() { return late > 0; }
    }

    private final Context ctx;
    private final Spec spec;
    private final boolean isSymmetryBreaking;

    private final Map<String, IntExpr> start = new LinkedHashMap<>();
    private final Map<String, IntExpr> proc = new LinkedHashMap<>();
    private final Map<String, IntExpr> late = new LinkedHashMap<>();
    private final Map<String, Long> dur = new LinkedHashMap<>();
    private final Map<String, Long> dl = new LinkedHashMap<>();

    public Encoder(Context ctx, Spec spec, boolean isSymmetryBreaking) {
        this.ctx = ctx;
        this.spec = spec;
        this.isSymmetryBreaking = isSymmetryBreaking;
        for (Spec.TaskDef t : spec.tasks()) {
            start.put(t.id(), ctx.mkIntConst("start_" + t.id()));
            proc.put(t.id(),  ctx.mkIntConst("proc_"  + t.id()));
            dur.put(t.id(), t.duration());
            dl.put(t.id(), t.deadline());
        }
    }

    // ---- entry points ----

    /** Hard-deadline satisfiability. */
    public void assertInto(Solver solver) {
        addCore(solver::add);
        addHardDeadlines(solver::add);
        if (isSymmetryBreaking) addSymmetry(solver::add);
    }

    /** Soft-deadline lateness minimisation. */
    public void assertLatenessInto(Optimize opt) {
        addCore(opt::Add);
        if (isSymmetryBreaking) addSymmetry(opt::Add);
        addLateness(opt);
    }

    // ---- shared constraints

    private void addCore(Consumer<BoolExpr> add) {
        IntNum zero  = ctx.mkInt(0);
        IntNum cores = ctx.mkInt(spec.processors());

        // start >= 0 ; 0 <= proc < N
        for (Spec.TaskDef t : spec.tasks()) {
            add.accept(ctx.mkGe(start.get(t.id()), zero));
            add.accept(ctx.mkGe(proc.get(t.id()), zero));
            add.accept(ctx.mkLt(proc.get(t.id()), cores));
        }

        // no two tasks overlap on the same core
        List<Spec.TaskDef> ts = spec.tasks();
        for (int i = 0; i < ts.size(); i++) {
            for (int j = i + 1; j < ts.size(); j++) {
                Spec.TaskDef a = ts.get(i), b = ts.get(j);
                IntExpr sa = start.get(a.id()), sb = start.get(b.id());
                ArithExpr<IntSort> ea = ctx.mkAdd(sa, ctx.mkInt(a.duration()));
                ArithExpr<IntSort> eb = ctx.mkAdd(sb, ctx.mkInt(b.duration()));
                add.accept(ctx.mkOr(ctx.mkDistinct(proc.get(a.id()), proc.get(b.id())), ctx.mkLe(ea, sb), ctx.mkLe(eb, sa)));
            }
        }

        // precedence: end(dep) <= start(t)
        for (Spec.TaskDef t : spec.tasks()) {
            for (String depId : t.after()) {
                ArithExpr<IntSort> endDep = ctx.mkAdd(start.get(depId), ctx.mkInt(dur.get(depId)));
                add.accept(ctx.mkLe(endDep, start.get(t.id())));
            }
        }
    }

    /** Hard deadline: start + duration <= deadline. (hard path only) */
    private void addHardDeadlines(Consumer<BoolExpr> add) {
        for (Spec.TaskDef t : spec.tasks()) {
            ArithExpr<IntSort> end = ctx.mkAdd(start.get(t.id()), ctx.mkInt(t.duration()));
            add.accept(ctx.mkLe(end, ctx.mkInt(t.deadline())));
        }
    }

    /** Soft deadline: late_t >= 0, late_t >= end_t - deadline_t, minimise Σ late_t. */
    private void addLateness(Optimize opt) {
        List<IntExpr> lates = new ArrayList<>();
        for (Spec.TaskDef t : spec.tasks()) {
            IntExpr lt = ctx.mkIntConst("late_" + t.id());
            late.put(t.id(), lt);
            lates.add(lt);

            ArithExpr<IntSort> end = ctx.mkAdd(start.get(t.id()), ctx.mkInt(t.duration()));
            opt.Add(ctx.mkGe(lt, ctx.mkInt(0)));
            opt.Add(ctx.mkGe(lt, ctx.mkSub(end, ctx.mkInt(t.deadline()))));
        }
        ArithExpr<IntSort> total = ctx.mkAdd(lates.toArray(new IntExpr[0]));
        opt.MkMinimize(total);
    }

    /** Schedulability-preserving symmetry breaking (value precedence). */
    private void addSymmetry(Consumer<BoolExpr> add) {
        List<Spec.TaskDef> ts = spec.tasks();
        if (ts.isEmpty()) return;

        IntExpr first = proc.get(ts.get(0).id());
        add.accept(ctx.mkEq(first, ctx.mkInt(0)));

        IntExpr prevMax = first;
        for (int i = 1; i < ts.size(); i++) {
            IntExpr pi = proc.get(ts.get(i).id());
            add.accept(ctx.mkLe(pi, ctx.mkAdd(prevMax, ctx.mkInt(1))));
            IntExpr maxI = ctx.mkIntConst("maxUsed_" + i);
            add.accept(ctx.mkEq(maxI, ctx.mkITE(ctx.mkGt(pi, prevMax), pi, prevMax)));
            prevMax = maxI;
        }
    }


    public List<Assignment> decode(Model model) {
        List<Assignment> out = new ArrayList<>();
        for (Spec.TaskDef t : spec.tasks()) {
            long s = ((IntNum) model.evaluate(start.get(t.id()), false)).getInt64();
            int  p = (int) ((IntNum) model.evaluate(proc.get(t.id()), false)).getInt64();
            out.add(new Assignment(t.id(), p, s, s + t.duration()));
        }
        return out;
    }

    public List<LateAssignment> decodeLateness(Model model) {
        List<LateAssignment> out = new ArrayList<>();
        for (Spec.TaskDef t : spec.tasks()) {
            long s = ((IntNum) model.evaluate(start.get(t.id()), false)).getInt64();
            int  p = (int) ((IntNum) model.evaluate(proc.get(t.id()), false)).getInt64();
            long l = ((IntNum) model.evaluate(late.get(t.id()), false)).getInt64();
            out.add(new LateAssignment(t.id(), p, s, s + t.duration(), dl.get(t.id()), l));
        }
        return out;
    }
}