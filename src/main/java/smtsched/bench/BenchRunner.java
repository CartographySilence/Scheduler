package smtsched.bench;

import smtsched.ground.Grounder;
import smtsched.ground.RustGrounder;
import smtsched.spec.Spec;
import smtsched.spec.SpecParser;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class BenchRunner {

    private static final int[] SIZES = { 10, 20, 40, 80 };
    private static final int[] CORES = { 2, 4, 8 };
    private static final double[] DENSITY = { 0.1, 0.3 };
    private static final double TIGHTNESS = 1.4;
    private static final long[] SEEDS = { 1, 2, 3 };
    private static final int TIMEOUT_MS = 60000;
    private static final Path SPEC_DIR = Path.of("bench", "specs");
    private static final Path OUT_CSV  = Path.of("bench", "results.csv");
    private static final Path GROUNDER_BIN = Path.of("rust-grounder", "target", "release", "rust-grounder");

    public static void main(String[] args) throws IOException {
        Grounder xmt = new RustGrounder(GROUNDER_BIN);
        List<BenchResult> results = new ArrayList<>();

        for (int n : SIZES) for (int c : CORES) for (double d : DENSITY) for (long seed : SEEDS) {
            Spec spec = Generator.random(n, c, d, TIGHTNESS, seed);
            results.add(Benchmark.run(spec, false, "gen", d, seed, TIMEOUT_MS));
            results.add(Benchmark.run(spec, true,  "gen", d, seed, TIMEOUT_MS));
            results.add(Benchmark.runGrounded(spec, false, "gen", d, seed, TIMEOUT_MS, xmt));
            results.add(Benchmark.runGrounded(spec, true,  "gen", d, seed, TIMEOUT_MS, xmt));
            results.add(Benchmark.runHybridRestarts(spec, "gen", d, seed));
        }
        results.addAll(runSpecFolder(xmt));

        write(results);
        summarise(results);
    }

    private static List<BenchResult> runSpecFolder(Grounder xmt) throws IOException {
        List<BenchResult> out = new ArrayList<>();
        if (!Files.isDirectory(SPEC_DIR)) return out;
        SpecParser parser = new SpecParser();
        try (Stream<Path> files = Files.list(SPEC_DIR)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                Spec spec = parser.parse(Files.readString(f));
                String name = f.getFileName().toString();
                out.add(Benchmark.run(spec, false, name, -1, -1, TIMEOUT_MS));
                out.add(Benchmark.run(spec, true,  name, -1, -1, TIMEOUT_MS));
                out.add(Benchmark.runGrounded(spec, false, name, -1, -1, TIMEOUT_MS, xmt));
                out.add(Benchmark.runGrounded(spec, true,  name, -1, -1, TIMEOUT_MS, xmt));
                out.add(Benchmark.runHybridRestarts(spec, name, -1, -1));
            }
        }
        return out;
    }

    private static void write(List<BenchResult> results) throws IOException {
        Files.createDirectories(OUT_CSV.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(BenchResult.csvHeader());
        results.forEach(r -> lines.add(r.toCsv()));
        Files.write(OUT_CSV, lines);
        System.out.println("wrote " + results.size() + " rows -> " + OUT_CSV.toAbsolutePath());
    }

    /** Each spec now produces 5 consecutive rows: hand-ground(off,on), grounded(off,on), restart-hybrid. */
    private static void summarise(List<BenchResult> results) {
        System.out.printf("%n%-6s %-6s %-6s %-8s %12s %12s %12s %12s %12s %10s%n",
                "src", "n", "cores", "density", "hg-off(ms)", "hg-on(ms)", "gr-off(ms)", "gr-on(ms)", "restart(ms)", "status");
        for (int i = 0; i + 4 < results.size(); i += 5) {
            BenchResult hgOff = results.get(i), hgOn = results.get(i + 1);
            BenchResult grOff = results.get(i + 2), grOn = results.get(i + 3);
            BenchResult restart = results.get(i + 4);
            System.out.printf("%-6s %-6d %-6d %-8.2f %12d %12d %12d %12d %12d %10s%n",
                    hgOff.source(), hgOff.nTasks(), hgOff.cores(), hgOff.density(),
                    hgOff.solveMs(), hgOn.solveMs(), grOff.solveMs(), grOn.solveMs(), restart.solveMs(), hgOff.status());
        }
    }
}