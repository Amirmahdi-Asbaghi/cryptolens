# CryptoLens

A small Java web app that fetches the top cryptocurrencies by market cap from CoinGecko, analyzes them, and serves a live dashboard with CSV and JSON export.

Built for a data-engineering exercise. Runs entirely in Docker; no Java or Maven installation required on the host.

## What it does

- Fetches the top 10 cryptocurrencies by market cap from CoinGecko's free API
- Filters by 24h direction (all / positive / negative)
- Sorts by growth, loss, price, or volume
- Converts USD prices and volumes to another currency via Frankfurter's live FX rates
- Computes the average 24h price change across the returned coins
- Exports results as CSV and JSON
- Serves everything through a single-page dashboard

## Requirements

- Docker Desktop (WSL2 backend on Windows, or native on macOS/Linux)
- That's it. Java and Maven run inside the container.

## Setup

### 1. Clone the repository

    git clone https://github.com/Amirmahdi-Asbaghi/cryptolens.git
    cd cryptolens

### 2. Configure the API key (optional but recommended)

CoinGecko's API works without a key, but the keyless tier is rate-limited to a few requests per minute. A free Demo key raises that to 30 requests/minute.

1. Sign up at https://www.coingecko.com/en/api/pricing
2. Open the Developer Dashboard, then API Keys, then Add New Key
3. Copy .env.example to .env and paste your key:

    COINGECKO_DEMO_KEY=CG-your-key-here

The .env file is gitignored, so your key never leaves your machine.

If you skip this step, the app still runs — it just falls back to keyless mode.

### 3. Build the image

    docker build -t cryptolens .

### 4. Run

With a Demo key:

    docker run --rm -p 8080:8080 --env-file .env -v "$(pwd)/out:/app/out" --name cryptolens cryptolens

Without a key:

    docker run --rm -p 8080:8080 -v "$(pwd)/out:/app/out" --name cryptolens cryptolens

On Windows PowerShell, replace $(pwd) with ${PWD}:

    docker run --rm -p 8080:8080 --env-file .env -v "${PWD}\out:/app/out" --name cryptolens cryptolens

### 5. Open the dashboard

http://localhost:8080

## API

### GET /api/coins

Returns the analyzed coin list plus summary stats.

Query parameters:

| Parameter | Values | Default |
|-----------|--------|---------|
| filter    | all, positive, negative | all |
| sort      | growth, loss, price, volume | growth |
| convert   | Any Frankfurter-supported currency code (EUR, GBP, JPY, ...) | USD |

Example:

    curl "http://localhost:8080/api/coins?filter=positive&sort=growth&convert=EUR"

Response shape:

    {
      "coins": [
        {
          "name": "Bitcoin",
          "symbol": "btc",
          "price": 75354.0,
          "change_24h": -4.40,
          "volume": 39284316844,
          "status": "loss"
        }
      ],
      "averageChange": -4.34,
      "count": 10
    }

### GET /api/coins.csv

Same query parameters. Returns the same data as a CSV download with columns: Name, Symbol, Price, Change_24h, Volume, Status.

## Output files

Every API call also writes a snapshot to the mounted out/ folder:

- out/cryptolens.json — the full result of the latest call
- out/cryptolens.csv — written when the CSV endpoint is hit

Both files are gitignored; they are generated artifacts, not source.

## Architecture

    src/main/java/com/cryptolens/
      Main.java                    Entry point; Javalin routes and pipeline wiring
      api/
        CoinGeckoClient.java       Fetches top N coins by market cap
        FrankfurterClient.java     Fetches live USD to target FX rate
      model/
        Coin.java                  Immutable record of one coin snapshot
      service/
        Analyzer.java              Pure functions: filter, sort, average
        Converter.java             Applies FX rate to prices and volumes
      io/
        CsvWriter.java             Writes coins to CSV
        JsonWriter.java            Writes coins to JSON

Design principles:

- One job per class. Clients do HTTP, services do math, writers do I/O.
- No framework beyond Javalin. The JDK's built-in HttpClient handles outbound requests; org.json handles parsing.
- Immutable data. Coin is a record; analysis returns new lists rather than mutating inputs.
- Secrets stay outside the repo. API keys are read from environment variables and injected at runtime via .env.

## Known limitations

- CoinGecko rate limit. The free keyless tier allows only a few requests per minute. The app surfaces a 503 with a clear message when this happens, but does not retry automatically. Adding a Demo key (see Setup) avoids it.
- No caching. Every page fetch hits CoinGecko. A production version would cache results for 60 seconds.
- No tests. The service layer is pure and would be easy to unit-test, but tests were not written for this exercise.
- Single currency pair at a time. Conversion goes USD to target. There is no support for arbitrary source currencies.

## Tech stack

| Layer        | Choice |
|--------------|--------|
| Language     | Java 17 |
| Build        | Maven (shaded JAR) |
| Web          | Javalin 6 |
| HTTP client  | JDK java.net.http.HttpClient |
| JSON         | org.json |
| Crypto data  | CoinGecko |
| FX data      | Frankfurter (ECB reference rates) |
| Runtime      | Docker (multi-stage build) |
| UI           | Static HTML + vanilla JavaScript, no framework |