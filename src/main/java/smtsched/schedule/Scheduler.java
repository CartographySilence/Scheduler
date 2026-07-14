package smtsched.schedule;

import com.microsoft.z3.Context;
import com.microsoft.z3.Optimize;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;

import smtsched.encode.Encoder;
import smtsched.encode.TextEncoder;
import smtsched.ground.Grounder;
import smtsched.spec.Spec;


import java.util.List;

public class Scheduler {

    public record Result(boolean schedulable, List<Encoder.Assignment> assignments) {}

    public record LateResult(boolean solved, List<Encoder.LateAssignment> assignments) {}

    public Result solve(Spec spec, boolean symmetryBreaking) {
        try (Context ctx = new Context()) {
            Solver z3 = ctx.mkSolver();
            Encoder encoder = new Encoder(ctx, spec, symmetryBreaking);
            encoder.assertInto(z3);
            if (z3.check() == Status.SATISFIABLE) {
                return new Result(true, encoder.decode(z3.getModel()));
            }
            return new Result(false, List.of());
        }
    }



    public LateResult solveLateness(Spec spec, boolean symmetryBreaking) {
        try (Context ctx = new Context()) {
            Optimize opt = ctx.mkOptimize();
            Encoder encoder = new Encoder(ctx, spec, symmetryBreaking);
            encoder.assertLatenessInto(opt);
            if (opt.Check() == Status.SATISFIABLE) {
                return new LateResult(true, encoder.decodeLateness(opt.getModel()));
            }
            return new LateResult(false, List.of());
        }
    }
}