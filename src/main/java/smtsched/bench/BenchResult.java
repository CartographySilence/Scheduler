package smtsched.bench;

/**
 * 
 * BenchResult
 * One row of the benchmark output (parameters of a run plus its outcome)
 * @param source original label (generated or a spec filename)
 * @param mode which pipeline produced this row
 * @param nTasks number of tasks in spec
 * @param cores number of processors in spec
 * @param density precedence density (-1 if not applicable)
 * @param seed RNG seed if used (-1 if not applicable)
 * @param symmetry whether symmetry-breaking is enabled
 * @param status Z3 result: "SATISFIABLE", "UNSATISFIABLE", or "UNKNOWN"
 * @param solveMs solve time in milliseconds
 * @param assertions number of assertions passed into the solver
 */
public record BenchResult(String source, String mode, int nTasks, int cores, double density, long seed, boolean symmetry, String status, long solveMs, int assertions) {
    public static String csvHeader() {
        return "source,mode,nTasks,cores,density,seed,symmetry,status,solveMs,assertions";
    }
    public String toCsv() {
        return String.join(",", source, mode, ""+nTasks, ""+cores, ""+density, ""+seed, ""+symmetry, status, ""+solveMs, ""+assertions);
    }
}