package com.sdprep.apigateway;

import java.net.URI;

/** Requests whose path starts with {@code pathPrefix} go to {@code backendBaseUri}. */
public record Route(String pathPrefix, URI backendBaseUri) {

    /** Matches whole path segments only: "/users" matches "/users/42" but not "/usersettings". */
    public boolean matches(String path) {
        return path.equals(pathPrefix) || path.startsWith(pathPrefix + "/");
    }

    /** The full backend URI for a request path (and optional query string). */
    public URI targetFor(String pathAndQuery) {
        return URI.create(backendBaseUri.toString() + pathAndQuery);
    }
}
