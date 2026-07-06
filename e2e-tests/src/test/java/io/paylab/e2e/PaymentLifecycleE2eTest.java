package io.paylab.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Phase 1 gate (black box, REST only): happy path with balanced ledger, idempotency replay,
 * refund reversal, illegal transitions. Requires `docker compose up -d --build` first.
 */
class PaymentLifecycleE2eTest {

    private static final String BASE = System.getenv().getOrDefault("PAYLAB_GATEWAY_URL", "http://localhost:8080");
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

    private static HttpResponse<String> post(String path, String idemKey, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .POST(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (idemKey != null) {
            builder.header("Idempotency-Key", idemKey);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String createBody(String payer, String merchant, String amount) {
        return """
                {"payerId":"%s","merchantId":"%s","sourceCurrency":"SGD","targetCurrency":"MYR","amount":%s}
                """
                .formatted(payer, merchant, amount);
    }

    private static JsonNode parse(HttpResponse<String> response) throws IOException {
        return json.readTree(response.body());
    }

    private String createPayment(String idemKey, String payer, String merchant) throws Exception {
        HttpResponse<String> created = post("/api/payments", idemKey, createBody(payer, merchant, "100.0000"));
        assertEquals(201, created.statusCode(), created.body());
        return parse(created).get("id").asText();
    }

    // ---------- scenarios ----------

    @Test
    void happyPath_create_capture_balancedLedger_timeline() throws Exception {
        String key = "e2e-" + UUID.randomUUID();
        HttpResponse<String> created =
                post("/api/payments", key, createBody("payer-e2e-1", "merchant-e2e-1", "100.0000"));
        assertEquals(201, created.statusCode(), created.body());
        assertTrue(
                created.headers().firstValue("X-PayLab-Trace-Id").isPresent(), "every API response carries a trace id");

        JsonNode payment = parse(created);
        String id = payment.get("id").asText();
        assertEquals("RISK_APPROVED", payment.get("status").asText());
        assertEquals(
                0, new BigDecimal("1.0000").compareTo(payment.get("feeAmount").decimalValue()), "1% fee on 100.0000");

        HttpResponse<String> captured = post("/api/payments/" + id + "/capture", "cap-" + key, null);
        assertEquals(200, captured.statusCode(), captured.body());
        JsonNode capturedPayment = parse(captured);
        assertEquals("CAPTURED", capturedPayment.get("status").asText());
        assertNotNull(capturedPayment.get("fxQuoteId").asText());
        // static table: SGD->MYR = 4.2/1.3 * 0.9985 = 3.2259230769; 100 * rate = 322.5923
        assertEquals(
                0,
                new BigDecimal("3.2259230769")
                        .compareTo(capturedPayment.get("fxRate").decimalValue()));
        assertEquals(
                0,
                new BigDecimal("322.5923")
                        .compareTo(capturedPayment.get("targetAmount").decimalValue()));

        JsonNode events = parse(get("/api/payments/" + id + "/events"));
        List<String> transitions = events.findValuesAsText("toStatus");
        assertEquals(List.of("CREATED", "RISK_APPROVED", "CAPTURED"), transitions);

        JsonNode trialBalance = parse(get("/api/trial-balance"));
        assertTrue(
                trialBalance.get("balanced").asBoolean(),
                "journal must net to zero per currency: " + trialBalance.get("netByCurrency"));
    }

    @Test
    void idempotency_createReplay_sameResult_captureReplay_keyReuseRejected() throws Exception {
        String key = "e2e-replay-" + UUID.randomUUID();
        String body = createBody("payer-e2e-2", "merchant-e2e-2", "50.0000");

        HttpResponse<String> first = post("/api/payments", key, body);
        HttpResponse<String> replay = post("/api/payments", key, body);
        assertEquals(201, first.statusCode());
        assertEquals(201, replay.statusCode());
        assertEquals(
                parse(first).get("id").asText(),
                parse(replay).get("id").asText(),
                "replay must return the original payment");
        assertEquals("true", replay.headers().firstValue("X-Idempotent-Replay").orElse(""), "replay must be marked");

        // same key, different body -> 422
        HttpResponse<String> misuse =
                post("/api/payments", key, createBody("payer-e2e-2", "merchant-e2e-2", "51.0000"));
        assertEquals(422, misuse.statusCode(), misuse.body());

        // capture replay returns the identical result
        String paymentId = parse(first).get("id").asText();
        String capKey = "cap-" + key;
        HttpResponse<String> cap1 = post("/api/payments/" + paymentId + "/capture", capKey, null);
        HttpResponse<String> cap2 = post("/api/payments/" + paymentId + "/capture", capKey, null);
        assertEquals(200, cap1.statusCode());
        assertEquals(200, cap2.statusCode());
        assertEquals(
                parse(cap1).get("fxQuoteId").asText(),
                parse(cap2).get("fxQuoteId").asText(),
                "capture replay must not lock a second quote");

        // missing header -> 400
        HttpResponse<String> missing = post("/api/payments", null, body);
        assertEquals(400, missing.statusCode());
    }

    @Test
    void refund_reversesLedger_andBlocksDoubleRefund() throws Exception {
        String base = "e2e-refund-" + UUID.randomUUID();
        String id = createPayment(base, "payer-e2e-3", "merchant-e2e-3");
        assertEquals(
                200,
                post("/api/payments/" + id + "/capture", base + "-cap", null).statusCode());

        HttpResponse<String> refunded = post("/api/payments/" + id + "/refund", base + "-ref", null);
        assertEquals(200, refunded.statusCode(), refunded.body());
        assertEquals("REFUNDED", parse(refunded).get("status").asText());

        JsonNode trialBalance = parse(get("/api/trial-balance"));
        assertTrue(trialBalance.get("balanced").asBoolean());

        // refund again (new key) -> illegal transition 409
        HttpResponse<String> again = post("/api/payments/" + id + "/refund", base + "-ref2", null);
        assertEquals(409, again.statusCode(), again.body());
        assertEquals("illegal_transition", parse(again).get("error").asText());

        // capture after refund -> 409
        HttpResponse<String> lateCapture = post("/api/payments/" + id + "/capture", base + "-cap2", null);
        assertEquals(409, lateCapture.statusCode());
    }

    @Test
    void validation_unsupportedCurrencyAndUnknownPayment() throws Exception {
        HttpResponse<String> bad = post(
                "/api/payments",
                "e2e-bad-" + UUID.randomUUID(),
                """
                {"payerId":"p","merchantId":"m","sourceCurrency":"THB","targetCurrency":"MYR","amount":10}
                """);
        assertEquals(400, bad.statusCode(), bad.body());

        HttpResponse<String> notFound = get("/api/payments/pay_does-not-exist");
        assertEquals(404, notFound.statusCode());
        assertFalse(parse(notFound).has("id"));
    }
}
