package smtsched.bench;

import smtsched.encode.Symmetry;

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
public record BenchResult(String source, String mode, int nTasks, int cores, double density, double tightness, long maxRelease, double utilisation, long seed, Symmetry symmetry, String status, long solveMs, long totalMs, int assertions) {
    public static String csvHeader() {
        return "source,mode,nTasks,cores,density,tightness,maxRelease,utilisation,seed,symmetry,status,solveMs,totalMs,assertions";
    }
    public String toCsv() {
        return String.join(",", source, mode, ""+nTasks, ""+cores, ""+density, ""+tightness, ""+maxRelease, ""+utilisation, ""+seed, ""+symmetry, status, ""+solveMs, ""+totalMs, ""+assertions);
    }
}