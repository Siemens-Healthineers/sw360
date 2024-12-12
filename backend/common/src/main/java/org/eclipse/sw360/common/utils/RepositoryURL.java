package org.eclipse.sw360.common.utils;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RepositoryURL {
    private static final Logger log = LogManager.getLogger(RepositoryURL.class);
    private static final String SCHEMA_PATTERN = ".+://(\\w*(?:[\\-@.\\\\s,_:/][/(.\\-)A-Za-z0-9]+)*)";

    private static final Map<String, String> VCS_HOSTS = Map.of(
            "github.com", "https://github.com/%s/%s",
            "gitlab.com", "https://gitlab.com/%s",
            "bitbucket.org", "https://bitbucket.org/%s/%s",
            "cs.opensource.google", "https://cs.opensource.google/%s/%s/%s",
            "go.googlesource.com", "https://go.googlesource.com/%s",
            "pypi.org", "https://pypi.org/project/%s"
    );

    private static String formatVCSUrl(String host, String[] urlParts) {
        String formattedUrl = null;

        switch (host) {
            case "github.com":
            case "bitbucket.org":
                if (urlParts.length >= 5) {
                    formattedUrl = String.format(VCS_HOSTS.get(host),
                            urlParts[3], urlParts[4].replaceAll("\\.git.*|#.*", ""));
                }
                break;

            case "gitlab.com":
                if (urlParts.length >= 5) {
                    int endIndex = Math.min(urlParts.length, 7);
                    String repoPath = String.join("/", Arrays.copyOfRange(urlParts, 3, endIndex));
                    repoPath = repoPath.replaceAll("\\.git.*|#.*", "");
                    formattedUrl = String.format(VCS_HOSTS.get(host), repoPath);
                }
                break;

            case "cs.opensource.google":
                if (urlParts.length >= 5) {
                    String thirdSegment = urlParts.length > 5 && !urlParts[5].isEmpty() && !urlParts[5].equals("+")
                            ? urlParts[5] : "";
                    formattedUrl = String.format(VCS_HOSTS.get(host), urlParts[3], urlParts[4], thirdSegment);
                }
                break;

            case "go.googlesource.com":
                if (urlParts.length >= 4) {
                    formattedUrl = String.format(VCS_HOSTS.get(host), urlParts[3]);
                }
                break;

            case "pypi.org":
                if (urlParts.length >= 5) {
                    formattedUrl = String.format(VCS_HOSTS.get(host), urlParts[4].replaceAll("\\.git.*|#.*", ""));
                }
                break;
        }

        return formattedUrl;
    }

    private static String sanitizeVCSByHost(String vcs, String host) {
        String encodedVCS = URLEncoder.encode(vcs, StandardCharsets.UTF_8);

        try {
            URI uri = URI.create(encodedVCS);
            String[] urlParts = uri.getPath().split("/");

            // Format the URL based on the host and decoded path parts
            String formattedUrl = formatVCSUrl(host, urlParts);

            if (formattedUrl == null) {
                log.error("Unsupported domain vcs URL: ", host, vcs);
                return null;
            }
            return formattedUrl.endsWith("/") ? formattedUrl.substring(0, formattedUrl.length() - 1) : formattedUrl;

        } catch (IllegalArgumentException e) {
            log.error("Invalid URL format: {}", vcs, e);
            return null;
        }
    }

    public static String sanitizeVCS(String vcs) {
        for (String host : VCS_HOSTS.keySet()) {
            if (vcs.contains(host)) {
                return sanitizeVCSByHost(vcs, host);
            }
        }
        return vcs;
    }

    public static String getComponentNameFromVCS(String vcsUrl, boolean isGetVendorandName) {
        String compName = vcsUrl.replaceAll(SCHEMA_PATTERN, "$1");
        String[] parts = compName.split("/");

        String domain = parts[0];
        String[] pathParts = Arrays.copyOfRange(parts, 1, parts.length);

        if (VCS_HOSTS.containsKey(domain)) {
            switch (domain) {
                case "github.com":
                case "bitbucket.org":
                    if(pathParts.length >= 2){
                        if(isGetVendorandName){
                            return String.join("/", Arrays.copyOfRange(pathParts, 0, pathParts.length));
                        }else{
                            return pathParts[pathParts.length - 1];
                        }
                    }

                case "gitlab.com":
                case "cs.opensource.google":
                    if(pathParts.length >= 2){
                        if(isGetVendorandName){
                            return String.join("/", Arrays.copyOfRange(pathParts, 0, pathParts.length));
                        }else{
                            return String.join("/", Arrays.copyOfRange(pathParts, 1, pathParts.length));
                        }
                    }

                case"go.googlesource.com":
                    if(pathParts.length >= 1){
                        if(isGetVendorandName){
                            return String.join("/", domain, pathParts[pathParts.length - 1]);
                        }else{
                            return pathParts[pathParts.length - 1];
                        }
                    }

                case"pypi.org":
                    if(pathParts.length >= 2){
                        if(isGetVendorandName){
                            return String.join("/", domain, pathParts[pathParts.length - 1]);
                        }else{
                            return pathParts[pathParts.length - 1];
                        }
                    }
            }
        }

        if (parts.length >= 2) {
            if (isGetVendorandName) {
                return String.join("/", Arrays.copyOfRange(parts, 1, parts.length));
            } else {
                return parts[parts.length - 1];
            }
        }
        return compName;
    }
}