package smtsched.spec;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpecParser {
    
    private final ObjectMapper mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public Spec parse(String json) {
        try { return validate(mapper.readValue(json, RawSpec.class)); }
        catch (JsonProcessingException e) {throw malformed(e); }
    }

    public Spec parse(Path file) throws IOException {
        return parse(Files.readString(file));
    }

    private Spec validate(RawSpec raw) {
        if (raw == null) throw new SpecException("empty specification");
        if (raw.processors == null) throw new SpecException("missing 'processors'");
        if (raw.processors < 1) throw new SpecException("'processors' must be >= 1, got " + raw.processors);
        if (raw.tasks == null || raw.tasks.isEmpty())
            throw new SpecException("'tasks' must be a non-empty list");

        List<Spec.TaskDef> tasks = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < raw.tasks.size(); i++) {
            RawTask t = raw.tasks.get(i);
            String at = "tasks[" + i + "]";
            if (t == null) throw new SpecException(at + " is null");
            if (t.id == null || t.id.isBlank()) throw new SpecException(at + ": missing 'id'");
            if (!ids.add(t.id)) throw new SpecException("duplicate task id '" + t.id + "'");
            if (t.duration == null) throw new SpecException(t.id + ": missing 'duration'");
            if (t.deadline == null) throw new SpecException(t.id + ": missing 'deadline'");
            if (t.duration <= 0) throw new SpecException(t.id + ": 'duration' must be > 0");
            if (t.deadline <= 0) throw new SpecException(t.id + ": 'deadline' must be > 0");
            long release = t.release == null ? 0 : t.release;
            if (release < 0) throw new SpecException(t.id + ": 'release' must be >= 0");
            if (release + t.duration > t.deadline)
                throw new SpecException(t.id + ": infeasible, release " + release + " + duration " + t.duration + " > deadline " + t.deadline);
            tasks.add(new Spec.TaskDef(t.id, t.duration, t.deadline, release,
                    t.after == null ? List.of() : t.after));
        }

        for (Spec.TaskDef t : tasks)
            for (String dep : t.after()) {
                if (dep.equals(t.id())) throw new SpecException(t.id() + " cannot depend on itself");
                if (!ids.contains(dep)) throw new SpecException(t.id() + " depends on unknown task '" + dep + "'");
            }

        return new Spec(raw.processors, tasks);
    }

    private static SpecException malformed(JsonProcessingException e) {
        return new SpecException("malformed JSON: " + e.getOriginalMessage(), e);
    }
    private static final class RawSpec { public Integer processors; public List<RawTask> tasks; }
    private static final class RawTask { public String id; public Long duration; public Long deadline; public Long release; public List<String> after; }
    
}

