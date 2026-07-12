package smtsched.bench;

import smtsched.spec.Spec;
import java.util.*;

public final class Generator {
    private Generator() {}

    public static Spec random(int nTasks, int cores, double density, double tightness, long seed) {
        Random rng = new Random(seed);

        long[] dur = new long[nTasks];
        for (int i = 0; i < nTasks; i++) dur[i] = 1 + rng.nextInt(9);

        List<List<String>> after = new ArrayList<>();
        for (int j = 0; j < nTasks; j++) {
            List<String> preds = new ArrayList<>();
            for (int i = 0; i < j && preds.size() < 3; i++) {
                if (rng.nextDouble() < density) preds.add("T" + i);
            }
            after.add(preds);
        }

        long[] earliestFinish = new long[nTasks];
        for (int j = 0; j < nTasks; j++) {
            long start = 0;
            for (String p : after.get(j)) {
                start = Math.max(start, earliestFinish[Integer.parseInt(p.substring(1))]);
            }
            earliestFinish[j] = start + dur[j];
        }

        List<Spec.TaskDef> tasks = new ArrayList<>();
        for (int j = 0; j < nTasks; j++) {
            long deadline = Math.max(dur[j], Math.round(earliestFinish[j] * tightness));
            tasks.add(new Spec.TaskDef("T" + j, dur[j], deadline, after.get(j)));
        }
        return new Spec(cores, tasks);
    }
}