package com.cryptolens.service;

import com.cryptolens.model.Coin;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pure analysis over a list of coins: filtering, averaging, sorting.
 *
 * <p>No I/O, no HTTP, no formatting. Every method takes a list and returns a
 * new list or a number. This makes the class trivial to reason about and
 * cheap to test.
 */
public class Analyzer {

    /** Which subset of coins to keep. */
    public enum Filter {
        /** All coins, no filtering. */
        ALL,
        /** Only coins whose 24h change is >= 0. */
        POSITIVE,
        /** Only coins whose 24h change is < 0. */
        NEGATIVE
    }

    /** What field to sort by. */
    public enum SortBy {
        /** Biggest 24h gain first. */
        GROWTH,
        /** Biggest 24h loss first (most negative first). */
        LOSS,
        /** Highest price first. */
        PRICE,
        /** Highest 24h volume first. */
        VOLUME
    }

    /**
     * Keep only coins matching {@code filter}.
     *
     * @param coins  input coins
     * @param filter which subset to keep
     * @return a new list; input is not modified
     */
    public List<Coin> filter(List<Coin> coins, Filter filter) {
        return switch (filter) {
            case ALL -> coins;
            case POSITIVE -> coins.stream()
                    .filter(c -> c.change24h() >= 0)
                    .collect(Collectors.toList());
            case NEGATIVE -> coins.stream()
                    .filter(c -> c.change24h() < 0)
                    .collect(Collectors.toList());
        };
    }

    /**
     * Sort coins by the chosen field, largest value first.
     *
     * @param coins  input coins
     * @param sortBy which field to sort by
     * @return a new list; input is not modified
     */
    public List<Coin> sort(List<Coin> coins, SortBy sortBy) {
        Comparator<Coin> cmp = switch (sortBy) {
            case GROWTH -> Comparator.comparingDouble(Coin::change24h).reversed();
            case LOSS   -> Comparator.comparingDouble(Coin::change24h);
            case PRICE  -> Comparator.comparingDouble(Coin::priceUsd).reversed();
            case VOLUME -> Comparator.comparingDouble(Coin::volumeUsd).reversed();
        };
        return coins.stream().sorted(cmp).collect(Collectors.toList());
    }

    /**
     * Arithmetic mean of {@code change24h} across all coins.
     *
     * @return the average, or 0.0 for an empty list
     */
    public double averageChange(List<Coin> coins) {
        if (coins.isEmpty()) {
            return 0.0;
        }
        return coins.stream()
                .mapToDouble(Coin::change24h)
                .average()
                .orElse(0.0);
    }
}