package com.sdprep.payment.core;

/**
 * @param cardToken a token from the provider's client SDK. The raw card number never touches our servers,
 *                  which keeps us out of most PCI-DSS scope.
 */
public record CreatePaymentRequest(String merchantId, long amountCents, String currency, String cardToken) {

    void validate() {
        if (merchantId == null || merchantId.isBlank() || cardToken == null || cardToken.isBlank()) {
            throw new InvalidRequestException("merchantId and cardToken are required");
        }
        if (amountCents <= 0) {
            throw new InvalidRequestException("amountCents must be positive");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new InvalidRequestException("currency must be a 3-letter ISO code, e.g. INR");
        }
    }
}
