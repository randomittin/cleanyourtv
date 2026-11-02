package com.gathin.tvhome;

/** Pure reading of a publisher's {@code intent_uri} text, for the fallbacks when it cannot be parsed or opened. */
final class LaunchSpec {
    private LaunchSpec() {}

    /**
     * The package named in an "intent:" URI ({@code ...;package=com.x;end}), or null for a plain link or when
     * the URI names none. Used when the row's own package column is empty.
     */
    static String packageOf(String intentUri) {
        if (intentUri == null || !intentUri.startsWith("intent:")) return null;
        int end = intentUri.indexOf(";end");
        String params = end >= 0 ? intentUri.substring(0, end) : intentUri;
        for (String part : params.split(";")) {
            if (part.startsWith("package=") && part.length() > "package=".length()) {
                return part.substring("package=".length());
            }
        }
        return null;
    }

    /** True for text Intent.parseUri(.., URI_INTENT_SCHEME) can use: an "intent:" URI or an absolute link. */
    static boolean looksLaunchable(String intentUri) {
        if (intentUri == null) return false;
        String uri = intentUri.trim();
        return uri.startsWith("intent:") || uri.contains("://");
    }
}
