package smtsched;

import smtsched.cli.GanttView;
import smtsched.cli.ModeComparison;
import smtsched.spec.Spec;
import smtsched.spec.SpecParser;
import smtsched.schedule.Scheduler;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Usage:
 *   mvn compile exec:java                          -> schedule the sample, draw the chart
 *   mvn compile exec:java -Dexec.args="spec.json"  -> schedule a file
 *   mvn compile exec:java -Dexec.args="spec.json --compare"  -> mode timing table
 */
public class Main {

    public static void main(String[] args) throws Exception {
        boolean compare = args.length > 0 && args[args.length - 1].equals("--compare");
        String file = (args.length > 0 && !args[0].startsWith("--")) ? args[0] : null;

        String json = (file != null) ? Files.readString(Path.of(file)) : Samples.PIPELINE;
        Spec spec = new SpecParser().parse(json);

        if (compare) {
            ModeComparison.run(spec);
            return;
        }

        Scheduler.LateResult result = new Scheduler().solveLateness(spec, true);
        if (!result.solved()) {
            System.out.println("infeasible even with lateness (precedence cycle?)");
            return;
        }
        GanttView.print(result.assignments(), spec.processors());
    }
}