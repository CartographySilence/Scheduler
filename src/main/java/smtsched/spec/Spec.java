package smtsched.spec;

import java.util.List;


public record Spec(int processors, List<TaskDef> tasks) {

    public Spec { tasks = List.copyOf(tasks); }

    public record TaskDef(String id, long duration, long deadline, List<String> after) {
        public TaskDef { after = List.copyOf(after); }
    }
    
}
