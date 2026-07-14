package smtsched;

import smtsched.cli.GanttView;
import smtsched.cli.ModeComparison;
import smtsched.spec.Spec;
import smtsched.spec.SpecParser;
import smtsched.schedule.Scheduler;
import smtsched.encode.Encoder;
import smtsched.encode.Encoder.LateAssignment;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Usage:
 *   mvn compile exec:java -> schedule the sample, draw the chart
 *   mvn compile exec:java -Dexec.args="spec.json" -> schedule a file
 *   mvn compile exec:java -Dexec.args="spec.json --compare" -> mode timing table
 */
public class Main {

    public static void main(String[] args) throws Exception {
        boolean compare = args.length > 0 && args[args.length - 1].equals("--compare");
        String file = (args.length > 0 && !args[0].startsWith("--")) ? args[0] : null;

        String json = (file != null) ? Files.readString(Path.of(file)) : Samples.BIG;
        Spec spec = new SpecParser().parse(json);

        if (compare) {
            ModeComparison.run(spec);
            return;
        }

        Scheduler scheduler = new Scheduler();

        // Fast path: hard deadlines. Only if that's provably UNSAT do we pay for the
        // Optimize-based lateness solve, which is much slower.
        Scheduler.Result hard = scheduler.solve(spec, true);

        switch (hard.outcome()) {
            case SCHEDULABLE -> GanttView.print(toLate(hard.assignments(), spec), spec.processors());

            case UNSCHEDULABLE -> {
                System.out.println("No schedule meets every deadline — finding the best compromise...");
                Scheduler.LateResult best = scheduler.solveLateness(spec, true);
                if (best.solved()) {
                    GanttView.print(best.assignments(), spec.processors());
                } else {
                    System.out.println("Infeasible even with lateness (precedence cycle?)");
                }
            }

            case TIMEOUT -> System.out.println("Gave up — couldn't decide within the time budget. 🎃");
        }
    }

    /** A hard-SAT schedule meets every deadline, so lateness is zero throughout. */
    private static List<LateAssignment> toLate(List<Encoder.Assignment> assignments, Spec spec) {
        Map<String, Long> deadlines = spec.tasks().stream()
                .collect(Collectors.toMap(Spec.TaskDef::id, Spec.TaskDef::deadline));

        return assignments.stream()
                .map(a -> new LateAssignment(a.task(), a.core(), a.start(), a.end(),
                                             deadlines.get(a.task()), 0))
                .toList();
    }
}