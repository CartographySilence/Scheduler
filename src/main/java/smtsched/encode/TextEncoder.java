package smtsched.encode;

import smtsched.spec.Spec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TextEncoder {
    private final Spec spec;
    private final List<String> names = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();
    private final boolean symmetryBreaking;

    public TextEncoder(Spec spec, boolean symmetryBreaking) {
        this.spec = spec;
        this.symmetryBreaking = symmetryBreaking;
        for (Spec.TaskDef t : spec.tasks()) {
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

        s.append("(set-logic UFLIA)\n");
        s.append("(set-option :produce-models true)\n\n");

        // Decision Vars 
        s.append("(declare-fun start (Int) Int)\n");
        s.append("(declare-fun proc (Int) Int)\n\n");


        // Task Data 
        s.append("(declare-fun dur (Int) Int)\n");
        s.append("(declare-fun dl (Int) Int)\n");

        // Data itself, duration and dealine per task
        for (int i = 0; i < n; i++) {
            Spec.TaskDef t = spec.tasks().get(i);
            s.append("(assert (= (dur ").append(i).append(") ").append(t.duration()).append("))").append("   ; ").append(t.id()).append('\n');s.append("(assert (= (dl ").append(i).append(") ").append(t.deadline()).append("))\n");
        } 
        s.append('\n');


        s.append("(assert (forall ((i Int))\n");
        s.append("(=> (and (<= 0 i) (< i ").append(n).append("))\n");
        s.append("(and (>= (start i) 0)\n");
        s.append("(<= (+ (start i) (dur i)) (dl i))\n");
        s.append("(>= (proc i) 0)\n");
        s.append("(< (proc i) ").append(cores).append(")))))\n\n");

        //  No two tasks overlap on the same core
        s.append("(assert (forall ((i Int) (j Int))\n");
        s.append("(=> (and (<= 0 i) (< i j) (< j ").append(n).append("))\n");
        s.append("(or (distinct (proc i) (proc j))\n");
        s.append("(<= (+ (start i) (dur i)) (start j))\n");
        s.append("(<= (+ (start j) (dur j)) (start i))))))\n\n");

        // Precedence list
        List<String> edges = new ArrayList<>();
        for(Spec.TaskDef t : spec.tasks()) {
            for (String dependancy : t.after()) {
                edges.add("(assert (<= (+ (start " + index.get(dependancy) + ") (dur " + index.get(dependancy)+ ")) (start " + index.get(t.id()) + ")))   ; " + dependancy + " -> " + t.id());
            }
        }

        if(!edges.isEmpty()) {
            edges.forEach(e -> s.append(e).append('\n'));
            s.append('\n');
        }

        if(symmetryBreaking) {
            s.append(symmetryBreak(n));
        }

        s.append("(check-sat)\n(get-model)\n");
        return s.toString();
    }




    private String symmetryBreak(int n) {
        StringBuilder s = new StringBuilder();
        s.append("(declare-fun maxUsed (Int) Int)\n");
        s.append("(assert (= (proc 0) 0))\n");
        s.append("(assert (= (maxUsed 0) 0))\n");
        for (int i = 1; i < n; i++) {
            s.append("(assert (<= (proc ").append(i).append(") (+ (maxUsed ").append(i - 1).append(") 1)))\n");
            s.append("(assert (= (maxUsed ").append(i).append(") (ite (> (proc ").append(i)
             .append(") (maxUsed ").append(i - 1).append(")) (proc ").append(i)
             .append(") (maxUsed ").append(i - 1).append("))))\n");
        }
        return s.append('\n').toString();
    }
    
}
