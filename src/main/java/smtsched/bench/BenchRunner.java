package smtsched.bench;

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
    private static final int TIMEOUT_MS = 10000;  
    private static final Path SPEC_DIR = Path.of("bench", "specs");
    private static final Path OUT_CSV  = Path.of("bench", "results.csv");

    public static void main(String[] args) throws IOException {
        List<BenchResult> results = new ArrayList<>();

        for (int n : SIZES) for (int c : CORES) for (double d : DENSITY) for (long seed : SEEDS) {
            Spec spec = Generator.random(n, c, d, TIGHTNESS, seed);
            results.add(Benchmark.run(spec, false, "gen", d, seed, TIMEOUT_MS));
            results.add(Benchmark.run(spec, true,  "gen", d, seed, TIMEOUT_MS));
        }
        results.addAll(runSpecFolder());

        write(results);
        summarise(results);
    }

    private static List<BenchResult> runSpecFolder() throws IOException {
        List<BenchResult> out = new ArrayList<>();
        if (!Files.isDirectory(SPEC_DIR)) return out;
        SpecParser parser = new SpecParser();
        try (Stream<Path> files = Files.list(SPEC_DIR)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                Spec spec = parser.parse(Files.readString(f));
                String name = f.getFileName().toString();
                out.add(Benchmark.run(spec, false, name, -1, -1, TIMEOUT_MS));
                out.add(Benchmark.run(spec, true,  name, -1, -1, TIMEOUT_MS));
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

    private static void summarise(List<BenchResult> results) {
        System.out.printf("%n%-6s %-6s %-6s %-8s %10s %10s %10s%n",
                "src", "n", "cores", "density", "off(ms)", "on(ms)", "status");
        for (int i = 0; i + 1 < results.size(); i += 2) {
            BenchResult off = results.get(i), on = results.get(i + 1);
            if (off.symmetry() || !on.symmetry()) continue;
            System.out.printf("%-6s %-6d %-6d %-8.2f %10d %10d %10s%n",
                    off.source(), off.nTasks(), off.cores(), off.density(),
                    off.solveMs(), on.solveMs(), off.status());
        }
    }
}