package smtsched.bench;

import com.microsoft.z3.*;
import smtsched.encode.Encoder;
import smtsched.spec.Spec;

public final class Benchmark {
    private Benchmark() {}

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

            return new BenchResult(source, spec.tasks().size(), spec.processors(), density, seed, symmetry, status.toString(), ms, assertions);
        }
    }
}