package com.vaspshow.backend.support;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * SSRF guard for outbound fetches of <em>discovered</em> URLs (preflight HEAD/Range and
 * sample download). Auto-discovery pulls candidate data URLs from third-party metadata,
 * so before the backend dereferences one we require http(s) and that the host does not
 * resolve to a loopback / private / link-local / unique-local address (which would let a
 * crafted dataset URL reach internal services or the cloud metadata endpoint).
 *
 * <p>Connections to our own fixed API hosts (DataCite, Zenodo, ...) are not routed through
 * this guard — only the untrusted, discovered data URLs are.
 */
public final class OutboundUrlGuard {

  private OutboundUrlGuard() {
  }

  public static boolean isSafe(String url) {
    if (url == null || url.isBlank()) {
      return false;
    }
    URI uri;
    try {
      uri = URI.create(url.trim());
    } catch (IllegalArgumentException ex) {
      return false;
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      return false;
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return false;
    }
    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      if (addresses.length == 0) {
        return false;
      }
      for (InetAddress address : addresses) {
        if (isBlocked(address)) {
          return false;
        }
      }
      return true;
    } catch (UnknownHostException ex) {
      return false;
    }
  }

  private static boolean isBlocked(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    byte[] bytes = address.getAddress();
    // IPv6 unique-local fc00::/7
    if (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) {
      return true;
    }
    // IPv4 carrier-grade NAT 100.64.0.0/10 (often internal)
    if (bytes.length == 4 && (bytes[0] & 0xff) == 100 && (bytes[1] & 0xc0) == 64) {
      return true;
    }
    return false;
  }
}
