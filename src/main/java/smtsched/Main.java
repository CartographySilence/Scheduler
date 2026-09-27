package smtsched;

import smtsched.cli.GanttView;
import smtsched.cli.ModeComparison;
import smtsched.spec.Spec;
import smtsched.spec.SpecParser;
import smtsched.schedule.Scheduler;
import smtsched.encode.Encoder;
import smtsched.encode.Encoder.LateAssignment;
import smtsched.ground.NoGround;
import smtsched.ground.RustGrounder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Interactive CLI: load a spec, pick a solve mode, see the schedule, loop.
 */
public class Main {

    private static final Path SPEC_DIR = Path.of("bench", "specs");
    private static final Path GROUNDER_BIN = Path.of("rust-grounder", "target", "release", "rust-grounder");

    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(System.in);
        Scheduler scheduler = new Scheduler();
        Spec spec = null;

        System.out.println("SMT Scheduler — interactive");

        while (true) {
            System.out.println();
            System.out.println("loaded: " + describe(spec));
            System.out.println("  [1] load a spec (" + SPEC_DIR + ")");
            System.out.println("  [2] load a built-in sample");
            System.out.println("  [3] solve");
            System.out.println("  [4] compare all modes");
            System.out.println("  [0] quit");
            System.out.print("> ");

            switch (in.nextLine().trim()) {
                case "1" -> spec = loadFromFile(in, spec);
                case "2" -> spec = loadSample(in);
                case "3" -> {
                    if (spec == null) { System.out.println("load a spec first."); break; }
                    solve(in, scheduler, spec);
                }
                case "4" -> {
                    if (spec == null) { System.out.println("load a spec first."); break; }
                    ModeComparison.run(spec);
                }
                case "0", "q", "quit" -> { return; }
                default -> System.out.println("not a valid option.");
            }
        }
    }

    private static String describe(Spec spec) {
        return spec == null ? "nothing" : spec.tasks().size() + " tasks, " + spec.processors() + " cores";
    }

    private static Spec loadFromFile(Scanner in, Spec fallback) {
        List<Path> files;
        try (Stream<Path> stream = Files.list(SPEC_DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            System.out.println("couldn't read " + SPEC_DIR + ": " + e.getMessage());
            return fallback;
        }

        if (files.isEmpty()) {
            System.out.println("no .json specs found in " + SPEC_DIR);
            return fallback;
        }

        for (int i = 0; i < files.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + files.get(i).getFileName());
        }
        System.out.print("> ");

        int choice;
        try {
            choice = Integer.parseInt(in.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("not a valid choice.");
            return fallback;
        }
        if (choice < 1 || choice > files.size()) {
            System.out.println("not a valid choice.");
            return fallback;
        }

        try {
            return new SpecParser().parse(files.get(choice - 1));
        } catch (Exception e) {
            System.out.println("couldn't load that spec: " + e.getMessage());
            return fallback;
        }
    }

    private static Spec loadSample(Scanner in) {
        System.out.println("  [1] pipeline (14 tasks, 1 core)");
        System.out.println("  [2] big (85 tasks, 6 cores)");
        System.out.print("> ");
        String json = in.nextLine().trim().equals("2") ? Samples.BIG : Samples.PIPELINE;
        return new SpecParser().parse(json);
    }

    private static void solve(Scanner in, Scheduler scheduler, Spec spec) {
        System.out.println("  1  basic");
        System.out.println("  2  built-in grounder");
        System.out.println("  3  xmt-lib grounder");
        System.out.println("  4  restart-search");
        System.out.println("  5  restart-search-hybrid");
        System.out.print("> ");
        String mode = in.nextLine().trim();

        Scheduler.Result result;
        switch (mode) {
            case "1" -> result = scheduler.solveText(spec, askSymmetry(in), new NoGround());
            case "2" -> result = scheduler.solve(spec, askSymmetry(in));
            case "3" -> result = scheduler.solveGrounded(spec, askSymmetry(in), new RustGrounder(GROUNDER_BIN));
            case "4" -> result = scheduler.solveWithRestarts(spec);
            case "5" -> result = scheduler.solveWithHybridRestarts(spec);
            default -> { System.out.println("not a valid mode."); return; }
        }

        report(in, scheduler, spec, result);
    }

    private static boolean askSymmetry(Scanner in) {
        System.out.print("symmetry breaking? [y/n]: ");
        return in.nextLine().trim().equalsIgnoreCase("y");
    }

    private static void report(Scanner in, Scheduler scheduler, Spec spec, Scheduler.Result result) {
        switch (result.outcome()) {
            case SCHEDULABLE -> GanttView.print(toLate(result.assignments(), spec), spec.processors());

            case UNSCHEDULABLE -> {
                System.out.println("no schedule meets every deadline.");
                System.out.print("try the optimize route for the best compromise? this will likely take a while [y/n]: ");
                if (!in.nextLine().trim().equalsIgnoreCase("y")) return;

                System.out.println("searching for minimum lateness...");
                Scheduler.LateResult best = scheduler.solveLateness(spec, true);
                if (best.solved()) {
                    GanttView.print(best.assignments(), spec.processors());
                } else {
                    System.out.println("infeasible even with lateness (precedence cycle?)");
                }
            }

            case TIMEOUT -> System.out.println("couldn't decide within the time budget.");
        }
    }

    /** A hard-SAT schedule meets every deadline, so lateness is zero throughout. */
    private static List<LateAssignment> toLate(List<Encoder.Assignment> assignments, Spec spec) {
        Map<String, Long> deadlines = spec.tasks().stream().collect(Collectors.toMap(Spec.TaskDef::id, Spec.TaskDef::deadline));
        return assignments.stream().map(a -> new LateAssignment(a.task(), a.core(), a.start(), a.end(), deadlines.get(a.task()), 0)).toList();
    }
}
