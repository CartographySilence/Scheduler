package smtsched.cli;

import smtsched.encode.Encoder.LateAssignment;

import java.util.Arrays;
import java.util.List;

/** Renders a schedule as a coloured CLI Gantt chart, paged to fit the terminal. */
public final class GanttView {

    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String DIM    = "\u001B[90m";
    private static final String GREEN  = "\u001B[42m\u001B[30m";
    private static final String RED    = "\u001B[41m\u001B[97m";
    private static final String REDTXT = "\u001B[91m";

    private static final int CELL       = 3;    // characters per time unit
    private static final int GUTTER     = 9;    // width of the "core N │ " label column
    private static final int MAX_WIDTH  = 120;  // assumed console width

    private GanttView() {}

    public static void print(List<LateAssignment> tasks, int cores) {
        if (tasks.isEmpty()) return;

        long makespan = tasks.stream().mapToLong(LateAssignment::end).max().orElse(0);

        // how many time units fit on one line, given the gutter and cell width
        int perPage = Math.max(1, (MAX_WIDTH - GUTTER) / CELL);
        int pages = (int) Math.ceil(makespan / (double) perPage);

        System.out.println("\n" + BOLD + "  schedule" + RESET
                + DIM + "   (green = on time, red = past deadline)" + RESET);

        for (int page = 0; page < pages; page++) {
            long from = (long) page * perPage;
            long to   = Math.min(from + perPage, makespan);

            if (pages > 1) {
                System.out.printf("%n" + DIM + "  ── t %d–%d " + "─".repeat(20)
                        + "  (%d/%d)" + RESET + "%n", from, to, page + 1, pages);
            }
            axis(from, to);
            border(from, to, "┌", "┐");
            for (int core = 0; core < cores; core++) row(tasks, core, from, to);
            border(from, to, "└", "┘");
        }

        legend(tasks);
        summary(tasks);
    }

    /** Time ruler for the visible window. */
    private static void axis(long from, long to) {
        StringBuilder axis = new StringBuilder(" ".repeat(GUTTER));
        for (long t = from; t < to; t++) {
            axis.append(t % 5 == 0 ? pad(Long.toString(t), CELL) : " ".repeat(CELL));
        }
        System.out.println(DIM + axis + RESET);
    }

    private static void border(long from, long to, String left, String right) {
        int width = (int) (to - from) * CELL;
        System.out.println(DIM + " ".repeat(GUTTER) + left + "─".repeat(width) + right + RESET);
    }

    /** One core's row, clipped to the [from, to) window. */
    private static void row(List<LateAssignment> tasks, int core, long from, long to) {
        int span = (int) (to - from);
        char[] label  = new char[span];
        String[] colour = new String[span];
        Arrays.fill(label, ' ');

        for (LateAssignment a : tasks) {
            if (a.core() != core) continue;

            // colour every occupied tick that falls in this window
            for (long t = Math.max(a.start(), from); t < Math.min(a.end(), to); t++) {
                colour[(int) (t - from)] = (t >= a.deadline()) ? RED : GREEN;
            }

            // centre the name over the bar, then clip to the window
            int barLen  = (int) (a.end() - a.start());
            long nameAt = a.start() + Math.max(0, (barLen - a.task().length()) / 2);
            for (int i = 0; i < a.task().length(); i++) {
                long t = nameAt + i;
                if (t >= from && t < to && t < a.end()) {
                    label[(int) (t - from)] = a.task().charAt(i);
                }
            }
        }

        StringBuilder row = new StringBuilder();
        for (int i = 0; i < span; i++) {
            String glyph = pad(label[i] == ' ' ? "" : String.valueOf(label[i]), CELL);
            row.append(colour[i] == null ? " ".repeat(CELL) : colour[i] + glyph + RESET);
        }
        System.out.printf(DIM + "core " + RESET + BOLD + "%-2d" + RESET + DIM + " │ " + RESET + "%s%n",
                core, row);
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