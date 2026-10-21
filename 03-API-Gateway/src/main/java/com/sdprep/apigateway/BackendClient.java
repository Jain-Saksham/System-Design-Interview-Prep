package com.sdprep.apigateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/** Forwards a request to a backend service with strict timeouts. */
public class BackendClient {

    /** What the backend returned. */
    public record BackendResponse(int status, String contentType, byte[] body) {
    }

    /** The backend didn't answer in time → the gateway returns 504. */
    public static class BackendTimeoutException extends Exception {
        BackendTimeoutException(URI target) {
            super("Backend timed out: " + target);
        }
    }

    /** The backend couldn't be reached → the gateway returns 502. */
    public static class BackendUnavailableException extends Exception {
        BackendUnavailableException(URI target) {
            super("Backend unavailable: " + target);
        }
    }

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public BackendClient(Duration connectTimeout, Duration requestTimeout) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
    }

    public BackendResponse forward(String method, URI target, Map<String, String> headers, byte[] body)
            throws BackendTimeoutException, BackendUnavailableException {
        HttpRequest.Builder request = HttpRequest.newBuilder(target)
                .timeout(requestTimeout) // never let a slow backend hold gateway resources forever
                .method(method, body.length == 0
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(request::header);

        try {
            HttpResponse<byte[]> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            return new BackendResponse(response.statusCode(), contentType, response.body());
        } catch (HttpTimeoutException e) {
            throw new BackendTimeoutException(target);
        } catch (IOException e) {
            throw new BackendUnavailableException(target);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackendUnavailableException(target);
        }
    }
}
