/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.config;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import lombok.Data;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

public class UserConfiguredUrlRestrictions {
  @Data
  public static class Builder {
    private String allowedHostnamesRegex = ".*";
    private List<String> allowedSchemes = new ArrayList<>(Arrays.asList("http", "https"));
    private boolean rejectLocalhost = true;
    private boolean rejectLinkLocal = true;
    private List<String> rejectedIps =
        new ArrayList<>(); // can contain IP addresses and/or IP ranges (CIDR block)

    public Builder withAllowedHostnamesRegex(String allowedHostnamesRegex) {
      setAllowedHostnamesRegex(allowedHostnamesRegex);
      return this;
    }

    public Builder withAllowedSchemes(List<String> allowedSchemes) {
      setAllowedSchemes(allowedSchemes);
      return this;
    }

    public Builder withRejectLocalhost(boolean rejectLocalhost) {
      setRejectLocalhost(rejectLocalhost);
      return this;
    }

    public Builder withRejectLinkLocal(boolean rejectLinkLocal) {
      setRejectLinkLocal(rejectLinkLocal);
      return this;
    }

    public Builder withRejectedIps(List<String> rejectedIpRanges) {
      setRejectedIps(rejectedIpRanges);
      return this;
    }

    public UserConfiguredUrlRestrictions build() {
      return new UserConfiguredUrlRestrictions(
          Pattern.compile(allowedHostnamesRegex),
          allowedSchemes,
          rejectLocalhost,
          rejectLinkLocal,
          rejectedIps);
    }
  }

  private final Pattern allowedHostnames;
  private final Set<String> allowedSchemes;
  private final boolean rejectLocalhost;
  private final boolean rejectLinkLocal;
  private final Set<String> rejectedIps;

  public UserConfiguredUrlRestrictions(
      Pattern allowedHostnames,
      Collection<String> allowedSchemes,
      boolean rejectLocalhost,
      boolean rejectLinkLocal,
      Collection<String> rejectedIps) {
    this.allowedHostnames = allowedHostnames;
    this.allowedSchemes =
        allowedSchemes == null
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new HashSet<>(allowedSchemes));
    this.rejectLocalhost = rejectLocalhost;
    this.rejectLinkLocal = rejectLinkLocal;
    this.rejectedIps =
        rejectedIps == null
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new HashSet<>(rejectedIps));
  }

  public URI validateURI(String url) throws IllegalArgumentException {
    try {
      URI u = URI.create(url).normalize();
      if (!u.isAbsolute()) {
        throw new IllegalArgumentException("non absolute URI " + url);
      }
      if (!allowedSchemes.contains(u.getScheme().toLowerCase())) {
        throw new IllegalArgumentException("unsupported URI scheme " + url);
      }
      if (!allowedHostnames.matcher(u.getHost()).matches()) {
        throw new IllegalArgumentException(
            "Host not allowed "
                + u.getHost()
                + ". Host much match "
                + allowedHostnames.toString()
                + ".");
      }

      if (rejectLocalhost || rejectLinkLocal) {
        InetAddress addr = InetAddress.getByName(u.getHost());
        if (rejectLocalhost) {
          if (addr.isLoopbackAddress()
              || Optional.ofNullable(NetworkInterface.getByInetAddress(addr)).isPresent()) {
            throw new IllegalArgumentException("invalid address for " + u.getHost());
          }
        }
        if (rejectLinkLocal && addr.isLinkLocalAddress()) {
          throw new IllegalArgumentException("invalid address for " + u.getHost());
        }
      }

      for (String ip : rejectedIps) {
        IpAddressMatcher ipMatcher = new IpAddressMatcher(ip);

        if (ipMatcher.matches(u.getHost())) {
          throw new IllegalArgumentException(
              "address " + u.getHost() + " is within rejected IPs: " + ip);
        }
      }

      return u;
    } catch (IllegalArgumentException iae) {
      throw iae;
    } catch (Exception ex) {
      throw new IllegalArgumentException("URI not valid " + url, ex);
    }
  }

  public Pattern getAllowedHostnames() {
    return allowedHostnames;
  }

  public Set<String> getAllowedSchemes() {
    return allowedSchemes;
  }

  public boolean isRejectLocalhost() {
    return rejectLocalhost;
  }

  public boolean isRejectLinkLocal() {
    return rejectLinkLocal;
  }
}
