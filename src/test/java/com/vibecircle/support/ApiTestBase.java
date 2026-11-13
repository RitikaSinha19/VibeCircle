package com.vibecircle.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibecircle.support.Api.Multipart;
import com.vibecircle.support.Api.Resp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Starts the whole application on a random port against the vibecircle_test database.
 * Each test class begins by deleting every user whose name starts with "it_" (their data cascades),
 * and every test creates its own fresh users, so tests never depend on each other.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:postgresql://localhost:5432/vibecircle?currentSchema=it_test",
                "spring.datasource.username=vibe",
                "spring.datasource.password=vibe_dev",
                "spring.flyway.schemas=it_test",
                "spring.flyway.default-schema=it_test",
                "spring.flyway.create-schemas=true"
        })
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ApiTestBase {

    protected static final String PASSWORD = "StrongPass123";
    /** PNG signature + padding: enough for the server's magic-byte check. */
    protected static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0};

    public record PostRef(long id, Long mediaId) {
    }

    @Value("${local.server.port}")
    private int port;
    @Value("${spring.datasource.url}")
    private String datasourceUrl;
    @Autowired
    private DataSource dataSource;

    protected Api api;
    protected JdbcClient db;

    @BeforeAll
    void setUpBase() {
                if (!datasourceUrl.contains("currentSchema=it_test")) {
            throw new IllegalStateException("Refusing to run: integration tests must use the it_test schema, not "
                    + datasourceUrl);
        }
        api = new Api("http://localhost:" + port);
        db = JdbcClient.create(dataSource);
        db.sql("DELETE FROM users WHERE username LIKE 'it\\_%'").update();
    }

    // ------------------------------------------------------------- basics
    protected static String json(Object... keyValues) {
        return Api.json(keyValues);
    }

    protected Resp expect(Resp resp, int status) {
        assertThat(resp.status()).as("HTTP status (response body: %s)", resp.body()).isEqualTo(status);
        return resp;
    }

    protected String uniqueName(String label) {
        return "it_" + label + "_" + Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000_000L), 36);
    }

    // -------------------------------------------------------------- users
    protected TestUser newUser(String label) {
        String username = uniqueName(label);
        expect(api.post("/api/auth/register", null, json("username", username, "email", username + "@example.test",
                "password", PASSWORD, "fullName", "IT " + label, "dateOfBirth", "1999-01-01")), 201);
        return login(username);
    }

    protected TestUser login(String username) {
        Resp r = expect(api.post("/api/auth/login", null, json("identifier", username, "password", PASSWORD)), 200);
        return new TestUser(r.longValue("user", "id"), username, r.text("accessToken"));
    }

    /** A user whose role is changed in the database, then logged in again so the token carries the role. */
    protected TestUser staff(String label, String role) {
        TestUser u = newUser(label);
        db.sql("UPDATE users SET role = :role WHERE id = :id").param("role", role).param("id", u.id()).update();
        return login(u.username());
    }

    protected void befriend(TestUser a, TestUser b) {
        Resp sent = expect(api.post("/api/friends/requests", a.token(), json("receiverId", b.id())), 201);
        expect(api.postEmpty("/api/friends/requests/" + sent.longValue("requestId") + "/accept", b.token()), 200);
    }

    // ---------------------------------------------------- reference data
    protected long interestId(String name) {
        return db.sql("SELECT id FROM interests WHERE name = :n").param("n", name).query(Long.class).single();
    }

    protected long cityId(String name) {
        return db.sql("SELECT id FROM cities WHERE name = :n").param("n", name).query(Long.class).single();
    }

    protected void setCityAndInterests(TestUser u, String city, String... interests) {
        expect(api.patch("/api/profiles/me", u.token(), json("cityId", cityId(city))), 200);
        String ids = Arrays.stream(interests).map(n -> String.valueOf(interestId(n))).collect(Collectors.joining(","));
        expect(api.put("/api/profiles/me/interests", u.token(), "{\"interestIds\":[" + ids + "]}"), 200);
    }

    // -------------------------------------------------------------- posts
    protected PostRef createPost(TestUser u, String caption, String audience, boolean withImage) {
        Multipart m = new Multipart().field("caption", caption).field("audience", audience);
        if (withImage) {
            m.file("files", "pic.png", "image/png", PNG);
        }
        Resp r = expect(api.multipart("/api/posts", u.token(), m), 201);
        return new PostRef(r.longValue("id"), withImage ? r.longValue("media", 0, "id") : null);
    }

    // -------------------------------------------------------- concurrency
    /** Fires the same call from `threads` threads at the same instant and returns every response. */
    protected List<Resp> runConcurrently(int threads, Supplier<Resp> call) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Resp>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.get();
                }));
            }
            ready.await();
            go.countDown();
            List<Resp> out = new ArrayList<>();
            for (Future<Resp> f : futures) {
                out.add(f.get(60, TimeUnit.SECONDS));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            pool.shutdownNow();
        }
    }

    protected static long countStatus(List<Resp> responses, int status) {
        return responses.stream().filter(r -> r.status() == status).count();
    }
}
