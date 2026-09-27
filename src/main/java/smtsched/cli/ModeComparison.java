package smtsched.cli;

import smtsched.schedule.Scheduler;
import smtsched.ground.Grounder;
import smtsched.ground.NoGround;
import smtsched.ground.RustGrounder;
import smtsched.spec.Spec;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Runs the same spec through each encoding mode and prints a timing table.
 *
 * Modes: basic (object encoder, n^2 loop) vs quantified text handed to z3, vs grounded via xmt-lib.
 */
public final class ModeComparison {

    private ModeComparison() {}

    public static void run(Spec spec) {
        Scheduler scheduler = new Scheduler();

        System.out.printf("%n  %-26s %-16s %10s%n", "mode", "outcome", "time");
        System.out.println("  " + "─".repeat(56));

        for (boolean sym : new boolean[]{ false, true })
            time("basic", sym, () -> scheduler.solve(spec, sym));

        for (boolean sym : new boolean[]{ false, true })
            time("quantified", sym, () -> scheduler.solveText(spec, sym, new NoGround()));

        Grounder xmt = new RustGrounder(Path.of("rust-grounder/target/release/rust-grounder"));
        for (boolean sym : new boolean[]{ false, true })
            time("grounded (xmt-lib)", sym, () -> scheduler.solveGrounded(spec, sym, xmt));
    }

    private static void time(String label, boolean symmetry, Supplier<Scheduler.Result> run) {
        long t0 = System.nanoTime();
        Scheduler.Result r = run.get();
        long ms = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("  %-26s %-16s %8d ms%n",
                label + (symmetry ? " + sym" : ""), r.outcome(), ms);
    }
}