package smtsched.ground;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class RustGrounder implements Grounder {
    private final Path binary;

    public RustGrounder(Path binary) { this.binary = binary; }
    
    @Override 
    public String ground(String xmtlib) {
        try {
            Process p = new ProcessBuilder(binary.toString()).start();

            Thread writer = new Thread(() -> {
                try (OutputStream in = p.getOutputStream()) {
                    in.write(xmtlib.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            writer.start();

            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            writer.join();
            p.waitFor();
            return out;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Grounding failed", e);
        }
    }
}
