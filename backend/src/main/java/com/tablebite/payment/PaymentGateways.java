package com.tablebite.payment;

import java.util.Optional;

/**
 * Resolves the configured online payment gateway. No gateway is bundled, so online
 * payment is reported as unavailable and the API rejects the ONLINE method instead of
 * pretending a payment succeeded. See {@link PaymentGateway} for how to add one.
 */
public final class PaymentGateways {

    private PaymentGateways() {
    }

    public static Optional<PaymentGateway> configured() {
        // Example once implemented:
        // if (System.getenv("PAYHERE_MERCHANT_SECRET") != null) {
        //     return Optional.of(new PayHereGateway(...));
        // }
        return Optional.empty();
    }

    public static boolean onlineEnabled() {
        return configured().isPresent();
    }
}
