package com.cryptolens.api;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches a live fiat exchange rate from Frankfurter's free public API.
 *
 * <p>Frankfurter publishes reference rates sourced from the European Central
 * Bank, updated once per working day. It requires no API key and has no
 * documented rate limit. Only ~30 major currencies are supported, which is
 * more than enough for USD→EUR-style conversion.
 *
 * <p>Scope: this class knows how to ask for one rate. It does not multiply
 * prices, cache results, or decide when conversion should happen. All of that
 * lives in the service layer.
 */
public class FrankfurterClient {

    /** Example: https://api.frankfurter.dev/v1/latest?base=USD&symbols=EUR */
    private static final String URL =
            "https://api.frankfurter.dev/v1/latest?base=%s&symbols=%s";

    private final HttpClient http;

    public FrankfurterClient() {
        // Same pattern as CoinGeckoClient: reuse one connection pool per client.
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Look up the current rate to convert 1 unit of {@code from} into {@code to}.
     *
     * @param from source currency code, e.g. {@code "USD"}
     * @param to   target currency code, e.g. {@code "EUR"}
     * @return the multiplier such that {@code amountInFrom * rate = amountInTo}
     * @throws IOException          on network failure
     * @throws InterruptedException if the calling thread is interrupted
     * @throws RuntimeException     on non-200 responses or an unknown currency
     */
    public double getRate(String from, String to) throws IOException, InterruptedException {
        String url = String.format(URL, from.toUpperCase(), to.toUpperCase());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Frankfurter returned HTTP " + response.statusCode()
                            + ": " + response.body());
        }

        return parse(response.body(), to);
    }

    /**
     * Pull the single rate out of Frankfurter's response.
     *
     * <p>Response shape: {@code {"base":"USD","date":"2026-09-15","rates":{"EUR":0.92}}}
     * If the target symbol is missing from {@code rates}, the currency isn't
     * supported — surface that clearly rather than returning a silent 1.0.
     */
    private double parse(String body, String target) {
        JSONObject root = new JSONObject(body);
        JSONObject rates = root.getJSONObject("rates");
        String key = target.toUpperCase();

        if (!rates.has(key)) {
            throw new RuntimeException(
                    "Frankfurter does not support currency: " + key);
        }
        return rates.getDouble(key);
    }
}