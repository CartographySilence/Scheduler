package smtsched.bench;

import com.microsoft.z3.*;
import smtsched.encode.Encoder;
import smtsched.encode.Symmetry;
import smtsched.encode.TextEncoder;
import smtsched.encode.XmtEncoder;
import smtsched.ground.Grounder;
import smtsched.ground.NoGround;
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
    public static BenchResult runExplicit(Spec spec, Symmetry symmetry, String source, double density, double tightness, long maxRelease, double utilisation, long seed, int timeoutMs) {
        long total0 = System.nanoTime();
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
            long totalMs = (System.nanoTime() - total0) / 1000000;

            return new BenchResult(source, "explicit", spec.tasks().size(), spec.processors(), density, tightness, maxRelease, utilisation, seed, symmetry, status.toString(), ms, totalMs, assertions);
        }
    }

    /** Quantified text handed straight to z3 — its own E-matching, no grounding step at all. */
    public static BenchResult runQuantified(Spec spec, Symmetry symmetry, String source, double density, double tightness, long maxRelease, double utilisation, long seed, int timeoutMs) {
        long total0 = System.nanoTime();
        TextEncoder encoder = new TextEncoder(spec, symmetry);
        String text = new NoGround().ground(encoder.encode());

        try (Context ctx = new Context()) {
            Solver z3 = ctx.mkSolver();

            Params p = ctx.mkParams();
            p.add("timeout", timeoutMs);
            z3.setParameters(p);

            z3.add(ctx.parseSMTLIB2String(text, null, null, null, null));

            int assertions = z3.getAssertions().length;
            long t0 = System.nanoTime();
            Status status = z3.check();
            long ms = (System.nanoTime() - t0) / 1000000;
            long totalMs = (System.nanoTime() - total0) / 1000000;

            return new BenchResult(source, "quantified", spec.tasks().size(), spec.processors(), density, tightness, maxRelease, utilisation, seed, symmetry, status.toString(), ms, totalMs, assertions);
        }
    }

    public static BenchResult runGrounded(Spec spec, Symmetry symmetry, String source, double density, double tightness, long maxRelease, double utilisation, long seed, int timeoutMs, Grounder grounder) {
        long total0 = System.nanoTime();
        XmtEncoder encoder = new XmtEncoder(spec, symmetry);
        String grounded = Scheduler.stripUnusedDeclarations(grounder.ground(encoder.encode()));

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
            long totalMs = (System.nanoTime() - total0) / 1000000;

            return new BenchResult(source, "grounded", spec.tasks().size(), spec.processors(), density, tightness, maxRelease, utilisation, seed, symmetry, status.toString(), ms, totalMs, assertions);
        }
    }

    public static BenchResult runRestarts(Spec spec, String source, double density, double tightness, long maxRelease, double utilisation, long seed) {
        long total0 = System.nanoTime();
        Scheduler scheduler = new Scheduler();

        long t0 = System.nanoTime();
        Scheduler.Result result = scheduler.solveWithRestarts(spec);
        long ms = (System.nanoTime() - t0) / 1000000;
        long totalMs = (System.nanoTime() - total0) / 1000000;

        String status = switch (result.outcome()) {
            case SCHEDULABLE -> "SATISFIABLE";
            case UNSCHEDULABLE -> "UNSATISFIABLE";
            case TIMEOUT -> "UNKNOWN";
        };

        return new BenchResult(source, "restart", spec.tasks().size(), spec.processors(), density, tightness, maxRelease, utilisation, seed, Symmetry.UNPERMUTED, status, ms, totalMs, -1);
    }
}