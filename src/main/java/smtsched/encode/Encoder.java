package smtsched.encode;

import com.microsoft.z3.*;
import smtsched.spec.Spec;

import java.util.*;
import java.util.function.Consumer;

/**
 *
 * Encoder
 * Builds the SMT encoding of a scheduling {@link Spec} for Z3 and decodes solved models back into concrete schedules
 * Variables (one entry pair per task)
 * {@code start_T}: integer start time
 * {@code proc_T}: integer core index, {@code 0 <= proc_T < cores}
 * {@code late_T}: integer lateness, only created for when soft-deadlines are used
 *
 * Two independent problem formulations share the same core constraints:
 *
 * Hard-SAT: Every task must meet its deadline 
 * Soft-deadlines: deadlines can be missed, solver aims to minimise total lateness across all tasks
 */
public class Encoder {

    /** A decoded hard-SAT entry for a single task */
    public record Assignment(String task, int core, long start, long end) {}

    /** A decoded soft-deadline entry, including how late the tasks will be */
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



    /**
     * Decalres one {@code start}/{@code proc} integer variable per task and caches 
     * each tasks duration for reuse in constraint building and decoding
     * @param ctx
     * @param spec
     * @param isSymmetryBreaking
     */
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



    /** Hard-deadline satisfiability. */
        public void assertBaseInto(Solver solver) {
        addCore(solver::add);
        addHardDeadlines(solver::add);
    }

    public void assertCoreSymmetryInto(Solver solver, int[] sigma) {
        addCoreSymmetry(solver::add, sigma);
    }

    /** Hard-deadline satisfiability. */
    public void assertInto(Solver solver) {
        assertBaseInto(solver);
        if (isSymmetryBreaking) assertCoreSymmetryInto(solver, derivePermutationFromGreedy());
    }

    /** Soft-deadline lateness minimisation. */
    public void assertLatenessInto(Optimize opt) {
        addCore(opt::Add);
        if (isSymmetryBreaking) addSymmetry(opt::Add);
        addLateness(opt);
    }


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

    private void addSymmetry(Consumer<BoolExpr> add) {
        addCoreSymmetry(add, derivePermutationFromGreedy());
    }

    private void addCoreSymmetry(Consumer<BoolExpr> add, int[] sigma) {
        List<Spec.TaskDef> ts = spec.tasks();
        if(ts.isEmpty()) return;

        IntExpr canon0 = canonExpr(ts.get(0), sigma, add);
        add.accept(ctx.mkEq(canon0, ctx.mkInt(0)));

        IntExpr prevMax = canon0;
        for(int i = 1; i < ts.size(); i++) {
            IntExpr canonI = canonExpr(ts.get(i), sigma, add);
            add.accept(ctx.mkLe(canonI, ctx.mkAdd(prevMax, ctx.mkInt(1))));
            IntExpr maxI = (IntExpr) ctx.mkFreshConst("maxUsed", ctx.getIntSort());
            add.accept(ctx.mkEq(maxI, ctx.mkITE(ctx.mkGt(canonI, prevMax), canonI, prevMax)));
            prevMax = maxI;
        }

    }

    private IntExpr canonExpr(Spec.TaskDef t, int[] sigma, Consumer<BoolExpr> add) {
        IntExpr p = proc.get(t.id());
        IntExpr canon = (IntExpr) ctx.mkFreshConst("canon_" + t.id(), ctx.getIntSort());

        Expr<IntSort> chain = ctx.mkInt(sigma[sigma.length - 1]);
        for(int k = sigma.length - 2; k >= 0; k--) {
            chain = ctx.mkITE(ctx.mkEq(p, ctx.mkInt(k)), ctx.mkInt(sigma[k]), chain);
        }

        add.accept(ctx.mkEq(canon, chain));
        return canon;
    }


    /**
     * Approach 1: derive a permutation from a greedy list, so the canonical label has a higher chance of lining up with Z3's own search
     * and what it is likely to find, instead of forcing task-index order
    */
    
    private int[] derivePermutationFromGreedy() {
        int n = spec.processors();
        Map<String, Integer> greedyCore = greedySchedule();

        int[] sigma = new int[n];
        Arrays.fill(sigma, -1);
        boolean[] used = new boolean[n];
        int nextLabel = 0;

        for(Spec.TaskDef t : spec.tasks()) {
            int c = greedyCore.get(t.id());
            if(!used[c]) {
                used[c] = true;
                sigma[c] = nextLabel++;
            }
        }

        for(int c = 0; c < n; c++) {
            if (sigma[c] == -1) {
                sigma[c] = nextLabel++;
            }
        }
        return sigma;
    }


    private Map<String, Integer> greedySchedule() {
        List<Spec.TaskDef> topo = topologicalOrder();
        int n = spec.processors();
        long[] coreFree = new long[n];
        Map<String, Long> finish = new HashMap<>();
        Map<String, Integer> core = new HashMap<>();

        for(Spec.TaskDef t : topo) {
            long earliestStart = 0;

            for (String dep : t.after()) {
                earliestStart = Math.max(earliestStart, finish.get(dep));
            }

            int bestCore = 0;
            long bestStart = Math.max(earliestStart, coreFree[0]);
            for (int c = 1; c < n; c++) {
                long s = Math.max(earliestStart, coreFree[c]);

                if (s < bestStart) { 
                    bestStart = s; bestCore = c; 
                }
            }

            long f = bestStart + t.duration();
            coreFree[bestCore] = f;
            finish.put(t.id(), f);
            core.put(t.id(), bestCore);
        }

        return core;
    }


    /** Khan's algorithm */
    private List<Spec.TaskDef> topologicalOrder() {
        Map<String, Spec.TaskDef> byId = new LinkedHashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();

        for(Spec.TaskDef t : spec.tasks()) {
            byId.put(t.id(), t);
            indegree.put(t.id(), t.after().size());
            for (String dep : t.after()) dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(t.id());
        }

        Deque<String> ready = new ArrayDeque<>();
        for (Spec.TaskDef t : spec.tasks()) {
            if (indegree.get(t.id()) == 0) {
                ready.add(t.id());
            }
        }

        List<Spec.TaskDef> order = new ArrayList<>();
        while(!ready.isEmpty()) {
            String id = ready.poll();
            order.add(byId.get(id));
            for(String dep : dependents.getOrDefault(id, List.of())) {
                if(indegree.merge(dep, -1, Integer::sum) == 0) {
                    ready.add(dep);
                }
            }
        }
        return order;
    }


    public List<Assignment> decode(Model model) {
        List<Assignment> out = new ArrayList<>();
        for (Spec.TaskDef t : spec.tasks()) {
            long s = ((IntNum) model.evaluate(start.get(t.id()), false)).getInt64();
            int p = (int) ((IntNum) model.evaluate(proc.get(t.id()), false)).getInt64();
            out.add(new Assignment(t.id(), p, s, s + t.duration()));
        }
        return out;
    }

    public List<LateAssignment> decodeLateness(Model model) {
        List<LateAssignment> out = new ArrayList<>();
        for (Spec.TaskDef t : spec.tasks()) {
            long s = ((IntNum) model.evaluate(start.get(t.id()), false)).getInt64();
            int p = (int) ((IntNum) model.evaluate(proc.get(t.id()), false)).getInt64();
            long l = ((IntNum) model.evaluate(late.get(t.id()), false)).getInt64();
            out.add(new LateAssignment(t.id(), p, s, s + t.duration(), dl.get(t.id()), l));
        }
        return out;
    }
}