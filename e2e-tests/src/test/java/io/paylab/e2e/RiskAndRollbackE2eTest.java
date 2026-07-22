package io.paylab.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Phase 2 gate (black box, REST only): risk-service verdicts drive create, and the Seata AT
 * forced-rollback proof — a fault injected after both branches must leave no trace in either
 * database. Requires `docker compose up -d --build` (chaos hook is on in compose).
 */
class RiskAndRollbackE2eTest {

    private static final String BASE = System.getenv().getOrDefault("PAYLAB_GATEWAY_URL", "http://localhost:8080");
    private static final String CHAOS_HEADER = "X-PayLab-Chaos";
    private static final String CHAOS_FAIL_CAPTURE = "fail-after-capture-branches";

    private static final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void waitForGateway() throws Exception {
        long deadline = System.currentTimeMillis() + 90_000;
        IOException last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> health = http.send(
                        HttpRequest.newBuilder(URI.create(BASE + "/actuator/health"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                if (health.statusCode() == 200 && health.body().contains("\"UP\"")) {
                    return;
                }
            } catch (IOException e) {
                last = e;
            }
            Thread.sleep(2000);
        }
        throw new IllegalStateException("gateway not healthy at " + BASE, last);
    }

    // ---------- helpers ----------

    private static HttpResponse<String> post(String path, String idemKey, String body, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .POST(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (idemKey != null) {
            builder.header("Idempotency-Key", idemKey);
        }
        headers.forEach(builder::header);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String createBody(String payer, String merchant, String amount) {
        return """
                {"payerId":"%s","merchantId":"%s","sourceCurrency":"SGD","targetCurrency":"MYR","amount":%s}
                """.formatted(payer, merchant, amount);
    }

    private static JsonNode parse(HttpResponse<String> response) throws IOException {
        return json.readTree(response.body());
    }

    /** True when the ledger's trial balance has any line for this account id. */
    private static boolean ledgerKnowsAccount(String accountId) throws Exception {
        JsonNode lines = parse(get("/api/trial-balance")).get("lines");
        List<String> accounts = lines.findValuesAsText("accountId");
        return accounts.contains(accountId);
    }

    // ---------- scenarios ----------

    @Test
    void denylistedPayerIsDeclinedAndTerminal() throws Exception {
        String key = "e2e-risk-" + UUID.randomUUID();
        HttpResponse<String> created =
                post("/api/payments", key, createBody("payer-denylisted", "merchant-e2e-risk", "50.0000"), Map.of());
        assertEquals(201, created.statusCode(), created.body());

        JsonNode payment = parse(created);
        assertEquals("RISK_DECLINED", payment.get("status").asText());
        String id = payment.get("id").asText();

        // decline reason is on the timeline
        JsonNode events = parse(get("/api/payments/" + id + "/events"));
        assertEquals(List.of("CREATED", "RISK_DECLINED"), events.findValuesAsText("toStatus"), events.toString());

        // terminal: capture is an illegal transition
        HttpResponse<String> capture = post("/api/payments/" + id + "/capture", key + "-cap", null, Map.of());
        assertEquals(409, capture.statusCode(), capture.body());
    }

    @Test
    void overCorridorCapIsDeclined() throws Exception {
        HttpResponse<String> created = post(
                "/api/payments",
                "e2e-cap-" + UUID.randomUUID(),
                createBody("payer-e2e-cap", "merchant-e2e-cap", "10000.0001"),
                Map.of());
        assertEquals(201, created.statusCode(), created.body());
        assertEquals("RISK_DECLINED", parse(created).get("status").asText());
    }

    @Test
    void forcedRollback_undoesLedgerAndStateTogether_thenSameKeyRetriesCleanly() throws Exception {
        // unique payer so the ledger account is this test's fingerprint
        String payer = "payer-e2e-chaos-" + UUID.randomUUID().toString().substring(0, 8);
        String payerAccount = "payer:" + payer;
        String key = "e2e-chaos-" + UUID.randomUUID();

        HttpResponse<String> created =
                post("/api/payments", key, createBody(payer, "merchant-e2e-chaos", "100.0000"), Map.of());
        assertEquals(201, created.statusCode(), created.body());
        String id = parse(created).get("id").asText();

        // fault injected AFTER the ledger branch and the local branch both did their work
        String capKey = "cap-" + key;
        HttpResponse<String> chaos =
                post("/api/payments/" + id + "/capture", capKey, null, Map.of(CHAOS_HEADER, CHAOS_FAIL_CAPTURE));
        assertEquals(500, chaos.statusCode(), chaos.body());
        assertEquals("chaos_injected", parse(chaos).get("error").asText(), chaos.body());

        // Seata proof #1: gateway branch rolled back — payment is still RISK_APPROVED
        assertEquals(
                "RISK_APPROVED", parse(get("/api/payments/" + id)).get("status").asText());
        // Seata proof #2: ledger branch rolled back — the payer account never reached the books
        assertFalse(ledgerKnowsAccount(payerAccount), "orphaned ledger legs survived the global rollback");

        // failure released the idempotency key: the SAME key retries to a clean success
        HttpResponse<String> retry = post("/api/payments/" + id + "/capture", capKey, null, Map.of());
        assertEquals(200, retry.statusCode(), retry.body());
        assertEquals("CAPTURED", parse(retry).get("status").asText());
        assertTrue(ledgerKnowsAccount(payerAccount), "successful capture must post the ledger legs");
        assertTrue(parse(get("/api/trial-balance")).get("balanced").asBoolean());
    }
}
