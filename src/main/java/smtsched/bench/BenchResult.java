package smtsched.bench;

public record BenchResult(String source, int nTasks, int cores, double density, long seed, boolean symmetry, String status, long solveMs, int assertions) {
    public static String csvHeader() {
        return "source,nTasks,cores,density,seed,symmetry,status,solveMs,assertions";
    }
    public String toCsv() {
        return String.join(",", source, ""+nTasks, ""+cores, ""+density, ""+seed, ""+symmetry, status, ""+solveMs, ""+assertions);
    }
}