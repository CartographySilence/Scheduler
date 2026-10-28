package smtsched.bench;

import smtsched.encode.Symmetry;
import smtsched.schedule.Scheduler;

import java.io.BufferedWriter;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class BenchRunner {

    private static final int[] A_LOADS = { 10, 20, 30 };
    private static final int[] A_CORES = { 2, 4, 6 };
    private static final double A_TIGHTNESS = 1.4;

    private static final int B_TASKS = 20;
    private static final int B_CORES = 2;
    private static final int B_STEPS = 8;

    private static final int[] C_TASKS = { 40, 60, 80 };
    private static final int C_CORES = 2;
    private static final double C_TIGHTNESS = 1.35;

    private static final int X_TASKS = 120;
    private static final int X_CORES = 12;
    private static final int X_SEEDS = 30;

    private static final double DENSITY = 0.3;
    private static final long[] SEEDS = { 1, 2, 3, 4, 5 };
    private static final long[] EXTENDED_SEEDS = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
    private static final long MAX_RELEASE = 10;
    private static final int WORKERS = 4;

    private static final Path OUT_CSV  = Path.of("bench", "results-60s.csv");

    private static final int ARMS = 4;

    public static void main(String[] args) throws Exception {
        runAll("warmup", 10, 2, A_TIGHTNESS, 0);

        ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
        List<Future<List<BenchResult>>> futures = new ArrayList<>();
        for (int load : A_LOADS) for (int c : A_CORES) {
            int n = load * c;
            if (n == 120 || n == 180) continue;
            long[] seeds = (n == 40 && c == 4) || (n == 60 && c == 6) ? EXTENDED_SEEDS : SEEDS;
            for (long seed : seeds)
                futures.add(pool.submit(() -> runAll("A", n, c, A_TIGHTNESS, seed)));
        }
        for (long seed = 1; seed <= X_SEEDS; seed++) {
            long s = seed;
            futures.add(pool.submit(() -> runAll("A", X_TASKS, X_CORES, A_TIGHTNESS, s)));
        }
        for (int step = 0; step <= B_STEPS; step++) for (long seed : SEEDS) {
            double tightness = (1000 + 25 * step) / 1000.0;
            futures.add(pool.submit(() -> runAll("B", B_TASKS, B_CORES, tightness, seed)));
        }
        for (int n : C_TASKS) for (long seed : SEEDS)
            futures.add(pool.submit(() -> runAll("C", n, C_CORES, C_TIGHTNESS, seed)));

        List<BenchResult> results = new ArrayList<>();
        Files.createDirectories(OUT_CSV.getParent());
        try (BufferedWriter out = Files.newBufferedWriter(OUT_CSV)) {
            out.write(BenchResult.csvHeader());
            out.newLine();
            out.flush();

            for (Future<List<BenchResult>> f : futures) {
                List<BenchResult> rows = f.get();
                for (BenchResult r : rows) {
                    out.write(r.toCsv());
                    out.newLine();
                }
                out.flush();
                results.addAll(rows);
            }
        } finally {
            pool.shutdown();
        }

        System.out.println("wrote " + results.size() + " rows -> " + OUT_CSV.toAbsolutePath());
        summarise(results);
    }

    private static List<BenchResult> runAll(String source, int nTasks, int cores, double tightness, long seed) {
        Generator.Instance inst = Generator.random(nTasks, cores, DENSITY, tightness, seed, MAX_RELEASE);
        double u = inst.utilisation();

        List<BenchResult> out = new ArrayList<>();
        out.add(Benchmark.runExplicit(inst.spec(), Symmetry.NONE, source, DENSITY, tightness, MAX_RELEASE, u, seed, Scheduler.TIMEOUT_MS));
        out.add(Benchmark.runExplicit(inst.spec(), Symmetry.UNPERMUTED, source, DENSITY, tightness, MAX_RELEASE, u, seed, Scheduler.TIMEOUT_MS));
        out.add(Benchmark.runExplicit(inst.spec(), Symmetry.GREEDY, source, DENSITY, tightness, MAX_RELEASE, u, seed, Scheduler.TIMEOUT_MS));
        out.add(Benchmark.runRestarts(inst.spec(), source, DENSITY, tightness, MAX_RELEASE, u, seed));
        return out;
    }

    private static void summarise(List<BenchResult> results) {
        System.out.printf("%n%-3s %-4s %-5s %-6s %-5s %-4s %9s %9s %9s %9s %14s%n",
                "src", "n", "cores", "tight", "util", "seed", "exp-non", "exp-unp", "exp-grd", "restart", "status");
        for (int i = 0; i + ARMS - 1 < results.size(); i += ARMS) {
            BenchResult first = results.get(i);
            StringBuilder row = new StringBuilder(String.format("%-3s %-4d %-5d %-6.3f %-5.2f %-4d",
                    first.source(), first.nTasks(), first.cores(), first.tightness(), first.utilisation(), first.seed()));
            for (int k = 0; k < ARMS; k++) row.append(String.format(" %9d", results.get(i + k).totalMs()));
            row.append(String.format(" %14s", first.status()));
            System.out.println(row);
        }
    }
}
