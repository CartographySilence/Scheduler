package smtsched.cli;

import smtsched.encode.Symmetry;
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

        System.out.printf("%n  %-32s %-16s %10s%n", "mode", "outcome", "time");
        System.out.println("  " + "─".repeat(62));

        for (Symmetry sym : Symmetry.values())
            time(label("basic", sym), () -> scheduler.solveText(spec, sym, new NoGround()));

        for (Symmetry sym : Symmetry.values())
            time(label("built-in grounder", sym), () -> scheduler.solve(spec, sym));

        Grounder xmt = new RustGrounder(Path.of("rust-grounder/target/release/rust-grounder"));
        for (Symmetry sym : Symmetry.values())
            time(label("xmt-lib grounder", sym), () -> scheduler.solveGrounded(spec, sym, xmt));

        time("restart-search", () -> scheduler.solveWithRestarts(spec));
        time("restart-search-hybrid", () -> scheduler.solveWithHybridRestarts(spec));
    }

    private static String label(String mode, Symmetry symmetry) {
        return symmetry == Symmetry.NONE ? mode : mode + " + " + symmetry.name().toLowerCase();
    }

    private static void time(String label, Supplier<Scheduler.Result> run) {
        long t0 = System.nanoTime();
        Scheduler.Result r = run.get();
        long ms = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("  %-32s %-16s %8d ms%n", label, r.outcome(), ms);
    }
}