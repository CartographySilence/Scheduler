package smtsched.test.java.smtsched;

import org.junit.jupiter.api.Test;
import smtsched.bench.Generator;
import smtsched.spec.Spec;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorTest {

    @Test void generatesRequestedShapeAndValidDag() {
        Spec s = Generator.random(30, 4, 0.3, 1.4, 42L);
        assertEquals(30, s.tasks().size());
        assertEquals(4, s.processors());

        Set<String> seen = new HashSet<>();
        for (Spec.TaskDef t : s.tasks()) {
            for (String dep : t.after())
                assertTrue(seen.contains(dep), t.id() + " depends on later/unknown " + dep);
            seen.add(t.id());
        }
    }

    @Test void isReproducibleForAFixedSeed() {
        Spec a = Generator.random(20, 4, 0.3, 1.4, 7L);
        Spec b = Generator.random(20, 4, 0.3, 1.4, 7L);
        assertEquals(a.tasks(), b.tasks());
    }
}