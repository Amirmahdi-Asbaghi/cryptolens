package com.cryptolens.service;

import com.cryptolens.api.FrankfurterClient;
import com.cryptolens.model.Coin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies an exchange rate to a list of coins, producing new Coin records
 * whose price and volume are expressed in the target currency.
 *
 * <p>The rate is fetched once per call, not per coin — otherwise a 10-coin
 * list would trigger 10 HTTP requests to Frankfurter and likely get rate
 * limited. Coins are immutable, so conversion returns a new list rather than
 * mutating the input.
 *
 * <p>The {@code change24h} percentage is currency-independent and is carried
 * through unchanged.
 */
public class Converter {

    private final FrankfurterClient fx;

    public Converter(FrankfurterClient fx) {
        this.fx = fx;
    }

    /**
     * Convert every coin's USD price and volume to {@code targetCurrency}.
     *
     * @param coins          source coins, priced in USD
     * @param targetCurrency ISO code like {@code "EUR"}, or {@code "USD"} to pass through
     * @return a new list; the input list is not modified
     * @throws IOException          if the rate lookup fails
     * @throws InterruptedException if the calling thread is interrupted
     */
    public List<Coin> convert(List<Coin> coins, String targetCurrency)
            throws IOException, InterruptedException {

        // Pass-through: no HTTP call, no new list allocation churn.
        if ("USD".equalsIgnoreCase(targetCurrency)) {
            return coins;
        }

        double rate = fx.getRate("USD", targetCurrency);

        List<Coin> result = new ArrayList<>(coins.size());
        for (Coin c : coins) {
            // status is derived from change24h, which doesn't change on conversion,
            // so we can reuse the existing value instead of recomputing it.
            result.add(new Coin(
                    c.id(), c.symbol(), c.name(),
                    c.priceUsd() * rate,
                    c.change24h(),
                    c.volumeUsd() * rate,
                    c.status()
            ));
        }
        return result;
    }
}