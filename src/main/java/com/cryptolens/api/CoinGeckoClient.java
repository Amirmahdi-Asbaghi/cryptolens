package com.cryptolens.api;

import com.cryptolens.model.Coin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches the top N coins by market cap from CoinGecko's public API.
 *
 * <p>Authentication is optional. If the {@code COINGECKO_DEMO_KEY} environment
 * variable is set, it's sent as the {@code x-cg-demo-api-key} header, which
 * raises the rate limit from the shared keyless tier (~3–4 calls/min) to a
 * dedicated 30 calls/min. If the variable is absent, requests go out
 * keyless — useful for a fresh clone that hasn't been configured yet.
 *
 * <p>The key is read once at construction and never logged. This class does
 * HTTP and parsing only; filtering, sorting, and conversion live in the
 * service layer.
 */
public class CoinGeckoClient {

    /** Free endpoint. Returns an array of market objects. */
    private static final String URL =
            "https://api.coingecko.com/api/v3/coins/markets" +
            "?vs_currency=usd&order=market_cap_desc&per_page=%d&page=1" +
            "&sparkline=false&price_change_percentage=24h";

    /** Env var that carries the optional Demo API key. */
    private static final String API_KEY_ENV = "COINGECKO_DEMO_KEY";

    private final HttpClient http;
    private final String apiKey;

    public CoinGeckoClient() {
        // One HttpClient per client instance: it holds a connection pool,
        // so reusing it across calls avoids a TCP+TLS handshake each time.
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Read the key once. Empty or missing => keyless mode.
        String key = System.getenv(API_KEY_ENV);
        this.apiKey = (key == null || key.isBlank()) ? null : key;

        // Log only whether a key is present — never the key itself.
        if (this.apiKey == null) {
            System.out.println("[CoinGeckoClient] No " + API_KEY_ENV
                    + " set — running keyless (a few calls/min).");
        } else {
            System.out.println("[CoinGeckoClient] Demo API key detected — "
                    + "using authenticated requests.");
        }
    }

    /**
     * Fetch the top {@code limit} coins by market cap.
     *
     * @param limit how many coins to return, 1–250
     * @return coins in the order CoinGecko returned them (market cap desc)
     * @throws IOException          on network failure
     * @throws InterruptedException if the calling thread is interrupted
     * @throws RuntimeException     on non-200 responses, with the status code
     */
    public List<Coin> fetchTop(int limit) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(String.format(URL, limit)))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");

        // CoinGecko accepts the Demo key as a header. Headers are preferred
        // over query params: query strings end up in server logs and browser
        // history, headers generally do not.
        if (apiKey != null) {
            builder.header("x-cg-demo-api-key", apiKey);
        }

        HttpResponse<String> response =
                http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            // Include the body: CoinGecko returns a JSON error message here.
            // 429 in particular is common enough to warrant a clear hint.
            String hint = response.statusCode() == 429
                    ? " (rate limited — wait 60 seconds and retry)"
                    : "";
            throw new RuntimeException(
                    "CoinGecko returned HTTP " + response.statusCode() + hint
                            + ": " + response.body());
        }

        return parse(response.body());
    }

    /** Parse CoinGecko's JSON array into {@link Coin} records. */
    private List<Coin> parse(String body) {
        JSONArray arr = new JSONArray(body);
        List<Coin> coins = new ArrayList<>(arr.length());

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);

            // price_change_percentage_24h can be null for very new listings.
            // Treat missing as 0 so one bad row doesn't kill the whole batch.
            double change = o.isNull("price_change_percentage_24h")
                    ? 0.0
                    : o.getDouble("price_change_percentage_24h");

            coins.add(Coin.of(
                    o.getString("id"),
                    o.getString("symbol"),
                    o.getString("name"),
                    o.getDouble("current_price"),
                    change,
                    o.getDouble("total_volume")
            ));
        }
        return coins;
    }
}