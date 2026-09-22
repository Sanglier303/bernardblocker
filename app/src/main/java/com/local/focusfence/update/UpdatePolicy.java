package com.local.focusfence.update;

import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Pure, shared update validation; also exercised by negative unit tests. */
public final class UpdatePolicy {
    public static final String CERTIFICATE = "8d62697bb934eb9fbaaff5a92c954575a036e8bf54a03988d4ddc6287055eecc";
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com",
            "github-releases.githubusercontent.com"));
    private UpdatePolicy() {}
    public static String hex(String value) {
        return value == null ? "" : value.replace(":", "").trim().toLowerCase(Locale.ROOT);
    }
    public static void manifest(long code, String name, String sha, String cert) {
        if (code <= 0 || code > Integer.MAX_VALUE || name == null
                || !name.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")
                || sha == null || !sha.matches("[0-9a-f]{64}") || !CERTIFICATE.equals(cert))
            throw new SecurityException("Manifeste de mise à jour invalide ou certificat inattendu");
    }
    public static boolean trustedUrl(URL url) {
        return "https".equalsIgnoreCase(url.getProtocol()) && url.getUserInfo() == null
                && (url.getPort() == -1 || url.getPort() == 443)
                && HOSTS.contains(url.getHost().toLowerCase(Locale.ROOT));
    }
    public static void candidate(String expectedPackage, long installedCode, String actualPackage,
                                 long actualCode, long expectedCode, String actualName, String expectedName,
                                 int minimumSdk, int deviceSdk, Set<String> installed, Set<String> incoming) {
        if (!expectedPackage.equals(actualPackage) || actualCode != expectedCode
                || actualCode <= installedCode || !expectedName.equals(actualName))
            throw new SecurityException("Package ou version de mise à jour incorrect");
        if (minimumSdk > deviceSdk) throw new SecurityException("Cette mise à jour exige une version Android plus récente");
        if (installed.size() != 1 || !installed.contains(CERTIFICATE)
                || incoming.size() != 1 || !incoming.contains(CERTIFICATE))
            throw new SecurityException("Signature Bernard officielle requise");
    }
}
