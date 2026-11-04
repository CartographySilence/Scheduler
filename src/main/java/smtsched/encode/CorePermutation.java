package smtsched.encode;

import smtsched.spec.Spec;

import java.util.*;

/**
 *
 * CorePermutation
 * Derives a core-labeling permutation from a cheap greedy schedule, for use as symmetry-breaking's
 * canonical order.
 */
public final class CorePermutation {
    private CorePermutation() {}

    public static int[] fromGreedySchedule(Spec spec) {
        int n = spec.processors();
        List<Spec.TaskDef> ts = spec.tasks();
        Map<String, Integer> greedyCore = greedySchedule(spec);

        int[] firstUse = new int[n];
        Arrays.fill(firstUse, -1);
        int nextRank = 0;

        for (Spec.TaskDef t : ts) {
            int c = greedyCore.get(t.id());
            if (firstUse[c] == -1) {
                firstUse[c] = nextRank++;
            }
        }

        Integer[] order = new Integer[ts.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingInt(i -> firstUse[greedyCore.get(ts.get(i).id())]));

        int[] out = new int[order.length];
        for (int i = 0; i < order.length; i++) out[i] = order[i];
        return out;
    }

    public static int[] unpermuted(Spec spec) {
        int[] order = new int[spec.tasks().size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        return order;
    }

    public static int[] shuffled(Spec spec, Random rng) {
        int[] order = unpermuted(spec);
        for (int i = order.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = order[i]; order[i] = order[j]; order[j] = tmp;
        }
        return order;
    }

    private static Map<String, Integer> greedySchedule(Spec spec) {
        List<Spec.TaskDef> topo = topologicalOrder(spec);
        int n = spec.processors();
        long[] coreFree = new long[n];
        Map<String, Long> finish = new HashMap<>();
        Map<String, Integer> core = new HashMap<>();

        for (Spec.TaskDef t : topo) {
            long earliestStart = t.release();

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
