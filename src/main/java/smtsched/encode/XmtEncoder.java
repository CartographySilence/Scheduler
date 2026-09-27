package smtsched.encode;

import smtsched.spec.Spec;

import java.util.*;

public class XmtEncoder {
    private final Spec spec;
    private final List<String> names = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();
    private final boolean symmetryBreaking;

    public XmtEncoder(Spec spec, boolean symmetryBreaking) {
        this.spec = spec;
        this.symmetryBreaking = symmetryBreaking;
        for(Spec.TaskDef t : spec.tasks()) {
            index.put(t.id(), names.size());
            names.add(t.id());
        }
    }

    public int taskCount() {
        return names.size();
    }

    public String taskName(int i) {
        return names.get(i);
    }

    public int indexOf(String taskID) {
        return index.get(taskID);
    }

    public String encode() {
        int n = names.size();
        int cores = spec.processors();
        StringBuilder s = new StringBuilder();

        s.append("(set-option :backend none)\n\n");

        s.append("(declare-fun start (Int) Int)\n");
        s.append("(declare-fun proc (Int) Int)\n\n");

        s.append("(declare-fun dur (Int) Int)\n");
        s.append("(declare-fun dl (Int) Int)\n");
        s.append("(declare-fun rel (Int) Int)\n");
        for (int i = 0; i < n; i++) {
            Spec.TaskDef t = spec.tasks().get(i);
            s.append("(assert (= (dur ")
             .append(i).append(") ")
             .append(t.duration())
             .append("))   ; ")
             .append(t.id()).append('\n');

            s.append("(assert (= (dl ")
             .append(i).append(") ")
             .append(t.deadline())
             .append("))\n");

            s.append("(assert (= (rel ")
             .append(i).append(") ")
             .append(t.release())
             .append("))\n");
        }
        s.append('\n');

        s.append("(declare-fun Task (Int) Bool)\n");
        s.append("(declare-fun Pair (Int Int) Bool)\n\n");

        s.append("(x-interpret-pred Task (x-set");
        for (int i = 0; i < n; i++) s.append(" (").append(i).append(")");
        s.append("))\n");

        s.append("(x-interpret-pred Pair (x-set");
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                s.append(" (").append(i).append(' ').append(j).append(")");
        s.append("))\n\n");

        s.append("(assert (forall ((i Int))\n");
        s.append("(=> (Task i)\n");
        s.append("(and (>= (start i) (rel i))\n");
        s.append("(<= (+ (start i) (dur i)) (dl i))\n");
        s.append("(>= (proc i) 0)\n");
        s.append("(< (proc i) ").append(cores).append(")))))\n\n");

        s.append("(assert (forall ((i Int) (j Int))\n");
        s.append("(=> (Pair i j)\n");
        s.append("(or (distinct (proc i) (proc j))\n");
        s.append("(<= (+ (start i) (dur i)) (start j))\n");
        s.append("(<= (+ (start j) (dur j)) (start i))))))\n\n");

        // Precedence list
        List<String> edges = new ArrayList<>();
        for (Spec.TaskDef t : spec.tasks()) {
            for (String dependancy : t.after()) {
                edges.add("(assert (<= (+ (start " + index.get(dependancy) + ") (dur " + index.get(dependancy)
                        + ")) (start " + index.get(t.id()) + ")))   ; " + dependancy + " -> " + t.id());
            }
        }
        if (!edges.isEmpty()) {
            edges.forEach(e -> s.append(e).append('\n'));
            s.append('\n');
        }

        if (symmetryBreaking) {
            s.append(symmetryBreak(n));
        }

        s.append("(x-ground)\n");
        return s.toString();
    }


    private String symmetryBreak(int n) {
        int[] sigma = CorePermutation.fromGreedySchedule(spec);
        StringBuilder s = new StringBuilder();

        s.append("(declare-fun canon (Int) Int)\n");
        for (int i = 0; i < n; i++) {
            s.append("(assert (= (canon ").append(i).append(") ").append(canonIte(i, sigma)).append("))\n");
        }
        s.append('\n');

        s.append("(declare-fun maxUsed (Int) Int)\n");
        s.append("(assert (= (canon 0) 0))\n");
        s.append("(assert (= (maxUsed 0) 0))\n");
        for (int i = 1; i < n; i++) {
            s.append("(assert (<= (canon ").append(i).append(") (+ (maxUsed ").append(i - 1).append(") 1)))\n");
            s.append("(assert (= (maxUsed ").append(i).append(") (ite (> (canon ").append(i)
             .append(") (maxUsed ").append(i - 1).append(")) (canon ").append(i)
             .append(") (maxUsed ").append(i - 1).append("))))\n");
        }
        return s.append('\n').toString();
    }

    private static String canonIte(int taskIndex, int[] sigma) {
        String chain = String.valueOf(sigma[sigma.length - 1]);
        for (int k = sigma.length - 2; k >= 0; k--) {
            chain = "(ite (= (proc " + taskIndex + ") " + k + ") " + sigma[k] + " " + chain + ")";
        }
        return chain;
    }

}
