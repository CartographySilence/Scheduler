package smtsched;

import smtsched.encode.Encoder.LateAssignment;
import smtsched.spec.Spec;
import smtsched.spec.SpecParser;
import smtsched.schedule.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[90m";
    private static final String GREEN = "\u001B[42m\u001B[30m";   // on-time: green bg, black text
    private static final String RED = "\u001B[41m\u001B[97m";   // overrun: red bg, white text
    private static final String REDTXT = "\u001B[91m";

    private static final int CELL = 3;   // characters per time unit

    public static void main(String[] args) throws Exception {
        String json = args.length > 0 ? Files.readString(Path.of(args[0])) : OVERTIGHT;
        Spec spec = new SpecParser().parse(json);

        long tOff = System.nanoTime();
        Scheduler.LateResult off = new Scheduler().solveLateness(spec, false);
        long msOff = (System.nanoTime() - tOff) / 1000000;

        long tOn = System.nanoTime();
        Scheduler.LateResult on = new Scheduler().solveLateness(spec, true);
        long msOn = (System.nanoTime() - tOn) / 1000000;

        System.out.printf("%n  %ssymmetry breaking%s   OFF: %d ms    ON: %d ms%n", BOLD, RESET, msOff, msOn);
        if (msOff > 0) {
            System.out.printf("  speedup: %.2fx%n", (double) msOff / Math.max(1, msOn));
        }

        if (!on.solved()) {
            System.out.println("infeasible even with lateness (precedence cycle?)");
            return;
        }
        printGantt(on.assignments(), spec.processors());
    }

    private static void printGantt(List<LateAssignment> tasks, int cores) {
        long makespan = tasks.stream().mapToLong(LateAssignment::end).max().orElse(0);
        int width = (int) makespan * CELL;

        System.out.println("\n" + BOLD + "  schedule" + RESET + DIM + "   (green = on time, red = past deadline)" + RESET);

        StringBuilder axis = new StringBuilder("        ");
        for (long t = 0; t <= makespan; t += 5) axis.append(pad(Long.toString(t), 5 * CELL));
        System.out.println(DIM + axis + RESET);
        System.out.println(DIM + "        ┌" + "─".repeat(width) + "┐" + RESET);

        for (int core = 0; core < cores; core++) {
            char[] cells = new char[(int) makespan];
            String[] colr = new String[(int) makespan];
            java.util.Arrays.fill(cells, ' ');

            for (LateAssignment a : tasks) {
                if (a.core() != core) continue;
                for (long t = a.start(); t < a.end(); t++) {
                    boolean overrun = t >= a.deadline();     // this time unit is past the deadline
                    colr[(int) t] = overrun ? RED : GREEN;
                }
                int span = (int) (a.end() - a.start());
                int off  = (int) a.start() + Math.max(0, (span - a.task().length()) / 2);
                for (int i = 0; i < a.task().length() && off + i < a.end(); i++)
                    cells[off + i] = a.task().charAt(i);
            }

            StringBuilder row = new StringBuilder();
            for (int t = 0; t < makespan; t++) {
                String c = colr[t];
                String glyph = pad(cells[t] == ' ' ? "" : String.valueOf(cells[t]), CELL);
                row.append(c == null ? "   " : c + glyph + RESET);
            }
            System.out.println(DIM + "core " + RESET + BOLD + core + RESET + DIM + " │ " + RESET + row);
        }
        System.out.println(DIM + "        └" + "─".repeat(width) + "┘" + RESET);

        System.out.println("\n  " + BOLD + "legend" + RESET);
        tasks.stream().sorted((x, y) -> Long.compare(x.start(), y.start())).forEach(a -> {
            String swatch = (a.isLate() ? RED : GREEN) + "  " + RESET;
            String note = a.isLate()
                    ? REDTXT + "LATE by " + a.late() + " (deadline " + a.deadline() + ")" + RESET
                    : DIM + "on time" + RESET;
            System.out.printf("    %s  %-8s core %d  t[%d–%d)  %s%n",
                    swatch, a.task(), a.core(), a.start(), a.end(), note);
        });

        long total = tasks.stream().mapToLong(LateAssignment::late).sum();
        long lateCount = tasks.stream().filter(LateAssignment::isLate).count();
        System.out.printf("%n  %d task(s) late, total lateness %d%n", lateCount, total);
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        int left = (width - s.length()) / 2;
        return " ".repeat(left) + s + " ".repeat(width - s.length() - left);
    }

    private static final String OVERTIGHT = """
{
  "processors": 6,
  "tasks": [
    { "id": "boot", "duration": 3, "deadline": 3 },
    { "id": "auth", "duration": 4, "deadline": 8,  "after": ["boot"] },
    { "id": "loadCfg", "duration": 2, "deadline": 6,  "after": ["boot"] },
    { "id": "indexA", "duration": 6, "deadline": 10 },
    { "id": "indexB", "duration": 6, "deadline": 10 },
    { "id": "indexC", "duration": 6, "deadline": 10 },
    { "id": "indexD", "duration": 6, "deadline": 12 },
    { "id": "query", "duration": 5, "deadline": 18, "after": ["auth", "indexA"] },
    { "id": "aggregate","duration": 4, "deadline": 20, "after": ["indexB", "indexC"] },
    { "id": "rank", "duration": 3, "deadline": 22, "after": ["query", "aggregate"] },
    { "id": "render", "duration": 4, "deadline": 24, "after": ["rank"] },
    { "id": "audit", "duration": 5, "deadline": 14 },
    { "id": "backup", "duration": 7, "deadline": 16 },
    { "id": "notify", "duration": 2, "deadline": 28, "after": ["render"] }
  ]
}
        """;
}