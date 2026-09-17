package smtsched.encode;

import smtsched.spec.Spec;

import java.util.*;

/**
 *
 * CorePermutation
 * Derives a core-labeling permutation from a cheap greedy schedule, for use as symmetry-breaking's
 * canonical order. No Z3 involved here — just Spec in, int[] out — so both Encoder and the text
 * encoders can call it.
 */
public final class CorePermutation {
    private CorePermutation() {}

    public static int[] fromGreedySchedule(Spec spec) {
        int n = spec.processors();
        Map<String, Integer> greedyCore = greedySchedule(spec);

        int[] sigma = new int[n];
        Arrays.fill(sigma, -1);
        boolean[] used = new boolean[n];
        int nextLabel = 0;

        for (Spec.TaskDef t : spec.tasks()) {
            int c = greedyCore.get(t.id());
            if (!used[c]) {
                used[c] = true;
                sigma[c] = nextLabel++;
            }
        }

        for (int c = 0; c < n; c++) {
            if (sigma[c] == -1) {
                sigma[c] = nextLabel++;
            }
        }
        return sigma;
    }

    private static Map<String, Integer> greedySchedule(Spec spec) {
        List<Spec.TaskDef> topo = topologicalOrder(spec);
        int n = spec.processors();
        long[] coreFree = new long[n];
        Map<String, Long> finish = new HashMap<>();
        Map<String, Integer> core = new HashMap<>();

        for (Spec.TaskDef t : topo) {
            long earliestStart = 0;

            for (String dep : t.after()) {
                earliestStart = Math.max(earliestStart, finish.get(dep));
            }

            int bestCore = 0;
            long bestStart = Math.max(earliestStart, coreFree[0]);
            for (int c = 1; c < n; c++) {
                long s = Math.max(earliestStart, coreFree[c]);

                if (s < bestStart) {
                    bestStart = s; bestCore = c;
                }
            }

            long f = bestStart + t.duration();
            coreFree[bestCore] = f;
            finish.put(t.id(), f);
            core.put(t.id(), bestCore);
        }

        return core;
    }

    /** Khan's algorithm */
    private static List<Spec.TaskDef> topologicalOrder(Spec spec) {
        Map<String, Spec.TaskDef> byId = new LinkedHashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();

        for (Spec.TaskDef t : spec.tasks()) {
            byId.put(t.id(), t);
            indegree.put(t.id(), t.after().size());
            for (String dep : t.after()) dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(t.id());
        }

        Deque<String> ready = new ArrayDeque<>();
        for (Spec.TaskDef t : spec.tasks()) {
            if (indegree.get(t.id()) == 0) {
                ready.add(t.id());
            }
        }

        List<Spec.TaskDef> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.poll();
            order.add(byId.get(id));
            for (String dep : dependents.getOrDefault(id, List.of())) {
                if (indegree.merge(dep, -1, Integer::sum) == 0) {
                    ready.add(dep);
                }
            }
        }
        return order;
    }
}
