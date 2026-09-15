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
 * <p>Uses the free {@code /coins/markets} endpoint, which returns all fields
 * we need in a single request: price, 24h change, and 24h volume. No API key
 * is required, but the free tier rate-limits aggressively — a few requests
 * per minute is safe, more will get HTTP 429.
 *
 * <p>This class does HTTP and parsing only. No filtering, no sorting, no
 * currency conversion — those belong to the service layer so they stay
 * testable without a network.
 */
public class CoinGeckoClient {

    /** Free endpoint, no key. Returns an array of market objects. */
    private static final String URL =
            "https://api.coingecko.com/api/v3/coins/markets" +
            "?vs_currency=usd&order=market_cap_desc&per_page=%d&page=1" +
            "&sparkline=false&price_change_percentage=24h";

    private final HttpClient http;

    public CoinGeckoClient() {
        // One HttpClient per client instance: it holds a connection pool,
        // so reusing it across calls avoids a TCP+TLS handshake each time.
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(URL, limit)))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            // Include the body: CoinGecko returns a JSON error message here,
            // and 429 in particular is common enough to warrant a clear hint.
            String hint = response.statusCode() == 429
                    ? " (rate limited — wait a minute and retry)"
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