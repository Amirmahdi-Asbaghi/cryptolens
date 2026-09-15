package com.cryptolens.io;

import com.cryptolens.model.Coin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes a list of coins to a CSV file with a fixed, human-readable header.
 *
 * <p>Columns are exactly: Name, Symbol, Price, Change_24h, Volume, Status.
 * Values are rounded to a sensible number of decimals so the file stays
 * readable — Excel and similar tools choke on 15-digit floats.
 *
 * <p>No CSV library is used: the schema is fixed, the data contains no
 * commas or quotes, and adding a dependency for six columns isn't worth it.
 */
public class CsvWriter {

    private static final String HEADER =
            "Name,Symbol,Price,Change_24h,Volume,Status";

    /**
     * Write {@code coins} to {@code path}, overwriting any existing file.
     *
     * @param coins the rows to write; order is preserved
     * @param path  destination file path (parent directories must exist)
     * @throws IOException if the file can't be written
     */
    public void write(List<Coin> coins, Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');

        for (Coin c : coins) {
            sb.append(escape(c.name())).append(',')
              .append(escape(c.symbol())).append(',')
              .append(String.format("%.4f", c.priceUsd())).append(',')
              .append(String.format("%.2f", c.change24h())).append(',')
              .append(String.format("%.2f", c.volumeUsd())).append(',')
              .append(c.status())
              .append('\n');
        }

        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Wrap a value in quotes if it contains a comma or double-quote.
     * CoinGecko's names are currently safe, but this keeps the writer
     * correct if that ever changes.
     */
    private String escape(String value) {
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}