package com.cryptolens.model;

/**
 * Immutable snapshot of one cryptocurrency at a point in time.
 *
 * <p>Kept as a record because it has no behavior and no identity beyond its
 * field values. Analysis happens in {@link com.cryptolens.service.Analyzer},
 * not here.
 *
 * @param id        CoinGecko's stable identifier, e.g. {@code "bitcoin"}
 * @param symbol    ticker symbol, e.g. {@code "btc"}
 * @param name      display name, e.g. {@code "Bitcoin"}
 * @param priceUsd  current price in USD as reported by CoinGecko
 * @param change24h percent change over the last 24h (negative means down)
 * @param volumeUsd 24h trading volume in USD
 * @param status    derived label: {@code "gain"} if change24h >= 0, else {@code "loss"}
 */
public record Coin(
        String id,
        String symbol,
        String name,
        double priceUsd,
        double change24h,
        double volumeUsd,
        String status
) {
    /** Convenience factory that computes {@code status} from {@code change24h}. */
    public static Coin of(String id, String symbol, String name,
                          double priceUsd, double change24h, double volumeUsd) {
        return new Coin(id, symbol, name, priceUsd, change24h, volumeUsd,
                change24h >= 0 ? "gain" : "loss");
    }
}