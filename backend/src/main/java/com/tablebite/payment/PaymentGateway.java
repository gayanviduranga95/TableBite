package com.tablebite.payment;

import com.tablebite.model.Order;

import java.util.Map;

/**
 * Integration point for an online card / wallet payment provider (e.g. PayHere,
 * Stripe). TableBite ships WITHOUT an online provider: cash and pay-at-counter are
 * confirmed by staff. To enable online payments:
 *
 * <ol>
 *   <li>Implement this interface for your provider (create a hosted checkout session
 *       with the provider's SDK/REST API using the order number and total).</li>
 *   <li>Register it in {@link PaymentGateways#configured()} when its secrets
 *       (e.g. PAYMENT_PROVIDER_SECRET) are present in the environment.</li>
 *   <li>Point the provider's webhook at {@code POST /api/payments/webhook}; the
 *       implementation must verify the provider signature before reporting success.</li>
 * </ol>
 *
 * Payments must only be marked PAID from a verified webhook - never from the browser.
 */
public interface PaymentGateway {

    /** Provider name stored in payments.provider. */
    String name();

    /** Creates a hosted checkout for the order and returns the URL to redirect the customer to. */
    CheckoutSession createCheckout(Order order, String returnUrl) throws PaymentException;

    /** Verifies a provider webhook and returns the outcome (or throws if the signature is invalid). */
    WebhookResult verifyWebhook(Map<String, String> headers, byte[] body) throws PaymentException;

    record CheckoutSession(String redirectUrl, String providerReference) {
    }

    record WebhookResult(long orderId, boolean paid, String providerReference) {
    }

    class PaymentException extends Exception {
        public PaymentException(String message) {
            super(message);
        }
    }
}
