package smtsched.cli;

import smtsched.encode.Encoder.LateAssignment;

import java.util.Arrays;
import java.util.List;

/** Renders a schedule as a coloured CLI Gantt chart. */
public final class GanttView {

    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String DIM    = "\u001B[90m";
    private static final String GREEN  = "\u001B[42m\u001B[30m";   // on time
    private static final String RED    = "\u001B[41m\u001B[97m";   // past deadline
    private static final String REDTXT = "\u001B[91m";

    private static final int CELL = 3;   // characters per time unit

    private GanttView() {}

    public static void print(List<LateAssignment> tasks, int cores) {
        if (tasks.isEmpty()) return;

        long makespan = tasks.stream().mapToLong(LateAssignment::end).max().orElse(0);

        header(makespan);
        for (int core = 0; core < cores; core++) row(tasks, core, makespan);
        footer(makespan);
        legend(tasks);
        summary(tasks);
    }

    private static void header(long makespan) {
        System.out.println("\n" + BOLD + "  schedule" + RESET + DIM + "   (green = on time, red = past deadline)" + RESET);

        StringBuilder axis = new StringBuilder("        ");
        for (long t = 0; t <= makespan; t += 5) axis.append(pad(Long.toString(t), 5 * CELL));
        System.out.println(DIM + axis + RESET);
        System.out.println(DIM + "        ┌" + "─".repeat((int) makespan * CELL) + "┐" + RESET);
    }

    private static void row(List<LateAssignment> tasks, int core, long makespan) {
        char[] label = new char[(int) makespan];
        String[] colour = new String[(int) makespan];
        Arrays.fill(label, ' ');

        for (LateAssignment a : tasks) {
            if (a.core() != core) continue;

            for (long t = a.start(); t < a.end(); t++) {
                colour[(int) t] = (t >= a.deadline()) ? RED : GREEN;
            }

            int span = (int) (a.end() - a.start());
            int from = (int) a.start() + Math.max(0, (span - a.task().length()) / 2);
            for (int i = 0; i < a.task().length() && from + i < a.end(); i++) {
                label[from + i] = a.task().charAt(i);
            }
        }

        StringBuilder row = new StringBuilder();
        for (int t = 0; t < makespan; t++) {
            String glyph = pad(label[t] == ' ' ? "" : String.valueOf(label[t]), CELL);
            row.append(colour[t] == null ? " ".repeat(CELL) : colour[t] + glyph + RESET);
        }
        System.out.println(DIM + "core " + RESET + BOLD + core + RESET + DIM + " │ " + RESET + row);
    }

    private static void footer(long makespan) {
        System.out.println(DIM + "        └" + "─".repeat((int) makespan * CELL) + "┘" + RESET);
    }

    private static void legend(List<LateAssignment> tasks) {
        System.out.println("\n  " + BOLD + "legend" + RESET);
        tasks.stream()
             .sorted((a, b) -> Long.compare(a.start(), b.start()))
             .forEach(a -> System.out.printf("    %s  %-10s core %d  t[%d–%d)  %s%n",
                     (a.isLate() ? RED : GREEN) + "  " + RESET,
                     a.task(), a.core(), a.start(), a.end(),
                     a.isLate()
                        ? REDTXT + "LATE by " + a.late() + " (deadline " + a.deadline() + ")" + RESET
                        : DIM + "on time" + RESET));
    }

    private static void summary(List<LateAssignment> tasks) {
        long total = tasks.stream().mapToLong(LateAssignment::late).sum();
        long late  = tasks.stream().filter(LateAssignment::isLate).count();
        System.out.printf("%n  %d task(s) late, total lateness %d%n", late, total);
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        int left = (width - s.length()) / 2;
        return " ".repeat(left) + s + " ".repeat(width - s.length() - left);
    }
}