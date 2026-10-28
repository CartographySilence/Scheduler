package smtsched.bench;

import smtsched.spec.Spec;
import java.util.*;
/**
 * 
 * Generator
 * Generates a random {@link Spec} instances for benchmarking
 */
public final class Generator {
    private Generator() {}

    public record Instance(Spec spec, double utilisation) {}

    /**
     * Builds a random task set with a DAG precdence structure and deadlines dervied from each task's own earliest finish time
     *
     * Generation steps:
     * 1. Durations: each task gets randomly assigned a duration [1-9]
     * 2. Precedences: task {@code j} may depend on any earlier task {@code i < j} (to prevent cycles)
     * each included independently with probability called at 3 predecessors per task
     * 3. Earliest finish: computed by walkijng tasks in index order and taking the max earliest-finish over each task's predecessors and its own release time, plus it's own duration
     * This only works because predecessors always have a lower index (and the loop relies on that invariant rather than a general topological sort)
     * 4. Deadline: {@code round(earliestFInish * tightness)}, floored at the task's own duration so a deadline can never be shorter than the task itself
     *
     * @param nTasks
     * @param cores
     * @param density
     * @param tightness
     * @param seed
     * @return
     */
    public static Spec random(int nTasks, int cores, double density, double tightness, long seed) {
        return random(nTasks, cores, density, tightness, seed, 0).spec();
    }

    /** Same as {@link #random(int, int, double, double, long)}, but each task also gets a random release time in {@code [0, maxRelease]}. */
    public static Instance random(int nTasks, int cores, double density, double tightness, long seed, long maxRelease) {
        Random rng = new Random(seed);

        long[] dur = new long[nTasks];
        for (int i = 0; i < nTasks; i++) dur[i] = 1 + rng.nextInt(9);

        long[] release = new long[nTasks];
        if (maxRelease > 0) for (int i = 0; i < nTasks; i++) release[i] = rng.nextLong(maxRelease + 1);

        List<List<String>> after = new ArrayList<>();
        for (int j = 0; j < nTasks; j++) {
         
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < j; i++) {
                if (rng.nextDouble() < density) candidates.add(i);
            }

            if (candidates.size() > 3) {
                Collections.shuffle(candidates, rng);
                candidates = candidates.subList(0, 3);
            }
            Collections.sort(candidates);
            List<String> preds = new ArrayList<>();
            for (int i : candidates) preds.add("T" + i);
            after.add(preds);
        }

        long[] earliestFinish = new long[nTasks];
        for (int j = 0; j < nTasks; j++) {
            long start = release[j];
            for (String p : after.get(j)) {
                start = Math.max(start, earliestFinish[Integer.parseInt(p.substring(1))]);
            }
            earliestFinish[j] = start + dur[j];
        }

        long criticalPath = 0;
        long totalWork = 0;
        for (int j = 0; j < nTasks; j++) {
            criticalPath = Math.max(criticalPath, earliestFinish[j]);
            totalWork += dur[j];
        }
        long horizon = (long) Math.ceil(tightness * Math.max(criticalPath, (double) totalWork / cores) - 1e-9);

        List<Spec.TaskDef> tasks = new ArrayList<>();
        for (int j = 0; j < nTasks; j++) {
            long deadline = Math.max(dur[j], horizon);
            tasks.add(new Spec.TaskDef("T" + j, dur[j], deadline, release[j], after.get(j)));
        }
        return new Instance(new Spec(cores, tasks), (double) totalWork / (cores * horizon));
    }
}