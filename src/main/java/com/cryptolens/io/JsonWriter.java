package com.cryptolens.io;

import com.cryptolens.model.Coin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes a list of coins to a JSON file as an array of flat objects.
 *
 * <p>Shape: {@code [{"name":"Bitcoin","symbol":"btc","price":63000.12,...}, ...]}
 * This is the same shape the web API returns, so downstream consumers can use
 * one parser for both. Numeric rounding matches the CSV writer so the two
 * outputs never disagree on a value.
 */
public class JsonWriter {

    /**
     * Write {@code coins} to {@code path}, pretty-printed with 2-space indent.
     *
     * @param coins the rows to write; order is preserved
     * @param path  destination file path (parent directories must exist)
     * @throws IOException if the file can't be written
     */
    public void write(List<Coin> coins, Path path) throws IOException {
        JSONArray arr = new JSONArray();

        for (Coin c : coins) {
            JSONObject o = new JSONObject();
            o.put("name", c.name());
            o.put("symbol", c.symbol());
            o.put("price", round(c.priceUsd(), 4));
            o.put("change_24h", round(c.change24h(), 2));
            o.put("volume", round(c.volumeUsd(), 2));
            o.put("status", c.status());
            arr.put(o);
        }

        Files.writeString(path, arr.toString(2), StandardCharsets.UTF_8);
    }

    /** Round to {@code decimals} places so JSON and CSV agree on displayed values. */
    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}