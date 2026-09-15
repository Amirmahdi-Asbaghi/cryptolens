package com.cryptolens;

import com.cryptolens.api.CoinGeckoClient;
import com.cryptolens.api.FrankfurterClient;
import com.cryptolens.io.CsvWriter;
import com.cryptolens.io.JsonWriter;
import com.cryptolens.model.Coin;
import com.cryptolens.service.Analyzer;
import com.cryptolens.service.Converter;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Entry point and HTTP wiring for CryptoLens.
 *
 * <p>Owns three routes:
 * <ul>
 *   <li>{@code GET /}             — serves the static HTML dashboard</li>
 *   <li>{@code GET /api/coins}    — JSON array of analyzed coins</li>
 *   <li>{@code GET /api/coins.csv} — CSV download of the same data</li>
 * </ul>
 *
 * <p>Query parameters on both API routes:
 * {@code filter=all|positive|negative}, {@code sort=growth|loss|price|volume},
 * {@code convert=EUR} (any Frankfurter-supported currency; USD means no conversion).
 *
 * <p>This class deliberately contains no business logic. It parses query
 * strings, calls the service and API layers, and serializes the result.
 */
public class Main {

    /** Where CSV and JSON outputs are written inside the container. */
    private static final Path OUT_DIR = Path.of("/app/out");

    public static void main(String[] args) throws Exception {
        // Fail fast if the mounted output directory is missing — better than
        // discovering it at the first download request.
        Files.createDirectories(OUT_DIR);

        CoinGeckoClient crypto = new CoinGeckoClient();
        FrankfurterClient fx = new FrankfurterClient();
        Analyzer analyzer = new Analyzer();
        Converter converter = new Converter(fx);
        CsvWriter csv = new CsvWriter();
        JsonWriter json = new JsonWriter();

        Javalin app = Javalin.create();

        app.get("/", ctx -> {
            // Serve index.html straight from the classpath. Javalin's built-in
            // static-files handler crashes the whole server if the resource
            // folder is missing at startup; doing it by hand degrades to a
            // clean 404 instead.
            var stream = Main.class.getResourceAsStream("/index.html");
            if (stream == null) {
                ctx.status(404).result("index.html not found in classpath.");
                return;
            }
            ctx.contentType("text/html").result(new String(
                    stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        });

        // Shared pipeline for both API routes.
        app.get("/api/coins", ctx -> {
            List<Coin> coins = analyze(ctx, crypto, analyzer, converter);
            ctx.contentType("application/json").result(toJson(coins).toString(2));
        });

        app.get("/api/coins.csv", ctx -> {
            List<Coin> coins = analyze(ctx, crypto, analyzer, converter);
            Path file = OUT_DIR.resolve("cryptolens.csv");
            csv.write(coins, file);
            ctx.contentType("text/csv")
               .header("Content-Disposition", "attachment; filename=cryptolens.csv")
               .result(Files.readString(file));
        });

        app.start(8080);
    }

    /**
     * Run the fetch → filter → sort → convert pipeline using query params.
     * Also writes a JSON snapshot to {@code out/cryptolens.json} on each call,
     * so the file output requirement is satisfied without a separate endpoint.
     */
    private static List<Coin> analyze(Context ctx,
                                      CoinGeckoClient crypto,
                                      Analyzer analyzer,
                                      Converter converter) throws Exception {

        Analyzer.Filter filter = parseEnum(
                ctx.queryParam("filter"), Analyzer.Filter.class, Analyzer.Filter.ALL);
        Analyzer.SortBy sortBy = parseEnum(
                ctx.queryParam("sort"), Analyzer.SortBy.class, Analyzer.SortBy.GROWTH);
        String convertTo = ctx.queryParamAsClass("convert", String.class).getOrDefault("USD");

        List<Coin> coins = crypto.fetchTop(10);
        coins = analyzer.filter(coins, filter);
        coins = analyzer.sort(coins, sortBy);
        coins = converter.convert(coins, convertTo);

        // Persist a JSON snapshot for the "save to file" requirement.
        new JsonWriter().write(coins, OUT_DIR.resolve("cryptolens.json"));

        return coins;
    }

    /** Case-insensitive enum lookup; falls back to {@code fallback} if missing or invalid. */
    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type, E fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Serialize coins to a JSON array using the same field names as JsonWriter. */
    private static JSONArray toJson(List<Coin> coins) {
        JSONArray arr = new JSONArray();
        for (Coin c : coins) {
            JSONObject o = new JSONObject();
            o.put("name", c.name());
            o.put("symbol", c.symbol());
            o.put("price", c.priceUsd());
            o.put("change_24h", c.change24h());
            o.put("volume", c.volumeUsd());
            o.put("status", c.status());
            arr.put(o);
        }
        return arr;
    }
}