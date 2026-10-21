package com.sdprep.apigateway;

import java.util.Map;
import java.util.Optional;

/**
 * Maps API keys to client IDs.
 *
 * <p>Production: keys are stored hashed in a database and cached in the gateway, or the gateway
 * validates a signed JWT locally using the identity provider's public keys (no lookup per request).
 */
public class ApiKeyAuthenticator {

    private final Map<String, String> clientIdByApiKey;

    public ApiKeyAuthenticator(Map<String, String> clientIdByApiKey) {
        this.clientIdByApiKey = Map.copyOf(clientIdByApiKey);
    }

    public Optional<String> authenticate(String apiKey) {
        return apiKey == null ? Optional.empty() : Optional.ofNullable(clientIdByApiKey.get(apiKey));
    }
}
