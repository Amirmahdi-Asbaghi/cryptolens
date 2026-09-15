package com.cryptolens;

import io.javalin.Javalin;

/**
 * Entry point for the CryptoLens web app.
 *
 * <p>At this stage it only proves the Javalin + Docker pipeline works.
 * Real routes and business wiring are added in later steps.
 */
public class Main {

    public static void main(String[] args) {
        // Javalin auto-configures sensible defaults; we only set the port.
        Javalin app = Javalin.create().start(8080);

        // Smoke-test endpoint. Replaced by the real page in step 8.
        app.get("/", ctx -> ctx.result("CryptoLens is up"));
    }
}