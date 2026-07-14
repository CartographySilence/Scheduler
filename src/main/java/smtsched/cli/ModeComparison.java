package smtsched.cli;

import smtsched.schedule.Scheduler;
import smtsched.ground.NoGround;
import smtsched.spec.Spec;

import java.util.function.Supplier;

/**
 * Runs the same spec through each encoding mode and prints a timing table.
 *
 * Modes: hand-ground (object encoder, n^2 loop) vs quantified text handed to z3.
 * Each with symmetry breaking off and on. The xmt-lib row slots in here once the
 * binary exists — it is the same solveText call with a different Grounder.
 */
public final class ModeComparison {

    private ModeComparison() {}

    public static void run(Spec spec) {
        Scheduler scheduler = new Scheduler();

        System.out.printf("%n  %-26s %-16s %10s%n", "mode", "outcome", "time");
        System.out.println("  " + "─".repeat(56));

        for (boolean sym : new boolean[]{ false, true })
            time("hand-ground", sym, () -> scheduler.solve(spec, sym));

        for (boolean sym : new boolean[]{ false, true })
            time("quantified", sym, () -> scheduler.solveText(spec, sym, new NoGround()));

        // TODO: xmt-lib
    }

    private static void time(String label, boolean symmetry, Supplier<Scheduler.Result> run) {
        long t0 = System.nanoTime();
        Scheduler.Result r = run.get();
        long ms = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("  %-26s %-16s %8d ms%n",
                label + (symmetry ? " + sym" : ""), r.outcome(), ms);
    }
}