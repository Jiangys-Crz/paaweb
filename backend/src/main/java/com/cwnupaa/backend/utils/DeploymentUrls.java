package com.cwnupaa.backend.utils;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Public links and remote integrations belong to the deployment, not the source tree. */
public final class DeploymentUrls {
    private DeploymentUrls() { }

    public static String site() {
        return baseUrl("PUBLIC_SITE_URL", "http://localhost:3403");
    }

    public static String hydro() {
        return baseUrl("HYDRO_BASE_URL", "");
    }

    public static Set<String> imageHosts() {
        Set<String> hosts = new HashSet<>();
        hosts.add(URI.create(site()).getHost().toLowerCase(Locale.ROOT));
        String additional = System.getenv("QQ_BOT_IMAGE_HOSTS");
        if (additional != null) Arrays.stream(additional.split(",")).map(String::trim)
                .filter(host -> host.matches("[a-zA-Z0-9.-]+"))
                .map(host -> host.toLowerCase(Locale.ROOT)).forEach(hosts::add);
        return Set.copyOf(hosts);
    }

    private static String baseUrl(String name, String fallback) {
        String configured = System.getenv(name);
        String value = configured == null || configured.isBlank() ? fallback : configured.trim();
        if (value.isEmpty()) return value;
        URI uri = URI.create(value);
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalStateException(name + " must be an absolute HTTP(S) base URL without credentials, query or fragment");
        return value.replaceAll("/+$", "");
    }
}
