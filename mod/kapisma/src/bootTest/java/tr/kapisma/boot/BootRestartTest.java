package tr.kapisma.boot;

import java.nio.file.*;
import java.util.List;

public final class BootRestartTest {
    private static Path fixture(String name) throws Exception {
        Path root = Files.createTempDirectory("kapisma-restart-" + name + "-");
        Files.createDirectory(root.resolve("world"));
        Files.writeString(root.resolve("world/marker.txt"), "preserve old world");
        Files.writeString(root.resolve("world/level.dat"), "synthetic save marker");
        Files.writeString(root.resolve("server.properties"), "level-name=world\nlevel-seed=123\n");
        return root;
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static long count(Path root, String prefix) throws Exception {
        try (var files = Files.list(root)) {
            return files.filter(p -> p.getFileName().toString().startsWith(prefix)).count();
        }
    }
    private static void invalid(String name, String request) throws Exception {
        Path root = fixture(name);
        Files.writeString(root.resolve("kapisma_next.json"), request);
        Boot.prepare(root);
        require(Files.readString(root.resolve("world/marker.txt")).equals("preserve old world"), "invalid request damaged old world");
        require(Files.readString(root.resolve("server.properties")).contains("level-seed=123"), "invalid request changed seed");
        require(count(root, "kapisma-next-rejected-") == 1 && count(root, "kapisma-world-backup-") == 0, "invalid request not quarantined");
        require(!Files.exists(root.resolve("kapisma_next.json")), "pending malformed request remains");
        System.out.println("PASS invalid " + name);
    }
    public static void main(String[] args) throws Exception {
        invalid("malformed", "{ invalid JSON");
        invalid("missing", "{}");
        invalid("empty-fixed", "{\"seed\":\"\",\"class\":\"orman\"}");
        invalid("not-long", "{\"seed\":\"not-a-number\",\"class\":\"orman\"}");
        invalid("overflow", "{\"seed\":\"9223372036854775808\",\"class\":\"orman\"}");
        invalid("bad-class", "{\"seed\":\"1\",\"class\":\"../outside\"}");

        Path valid = fixture("valid");
        Files.writeString(valid.resolve("kapisma_next.json"), "{\"seed\":\"-335906671735727005\",\"class\":\"col_mesa\"}");
        Boot.prepare(valid);
        require(!Files.exists(valid.resolve("world")), "fresh world path must be empty");
        require(Files.readString(valid.resolve("server.properties")).contains("level-seed=-335906671735727005"), "new seed not committed");
        Path archive;
        try (var paths = Files.list(valid)) {
            archive = paths.filter(p -> p.getFileName().toString().startsWith("kapisma-world-backup-")).findFirst().orElseThrow();
        }
        require(Files.readString(archive.resolve("marker.txt")).equals("preserve old world"), "old save missing from archive");
        require(count(valid, "kapisma-next-applied-") == 1 && !Files.exists(valid.resolve("kapisma_next.json")), "request not consumed");
        System.out.println("PASS valid restart archives save and sets exact seed");

        Boot.prepare(valid);
        require(count(valid, "kapisma-world-backup-") == 1 && Files.readString(valid.resolve("server.properties")).contains("level-seed=-335906671735727005"), "second boot changed committed state");
        System.out.println("PASS repeat boot does not replace committed seed");

        Path unsafe = fixture("unsafe");
        Files.writeString(unsafe.resolve("server.properties"), "level-name=../outside\nlevel-seed=123\n");
        Files.writeString(unsafe.resolve("kapisma_next.json"), "{\"seed\":\"456\",\"class\":\"random\"}");
        boolean rejected = false;
        try { Boot.prepare(unsafe); } catch (java.io.IOException expected) { rejected = true; }
        require(rejected && Files.exists(unsafe.resolve("world/marker.txt")) && Files.exists(unsafe.resolve("kapisma_next.json")), "unsafe path not fail-closed");
        System.out.println("PASS unsafe world path refused");

        Path wrong = fixture("not-world");
        Files.writeString(wrong.resolve("server.properties"), "level-name=config\nlevel-seed=123\n");
        Files.createDirectory(wrong.resolve("config"));
        Files.writeString(wrong.resolve("config/settings.txt"), "keep");
        Files.writeString(wrong.resolve("kapisma_next.json"), "{\"seed\":\"456\",\"class\":\"random\"}");
        rejected = false;
        try { Boot.prepare(wrong); } catch (java.io.IOException expected) { rejected = true; }
        require(rejected && Files.readString(wrong.resolve("config/settings.txt")).equals("keep"), "non-world directory moved");
        System.out.println("PASS non-world directory refused");

        Path random = fixture("random");
        Files.writeString(random.resolve("kapisma_next.json"), "{\"seed\":\"\",\"class\":\"random\"}");
        Boot.prepare(random);
        require(count(random, "kapisma-world-backup-") == 1 && count(random, "kapisma-next-applied-") == 1, "random request failed");
        System.out.println("PASS explicit random restart");
        System.out.println("BOOT RESTART 11/11 PASS");
    }
}
