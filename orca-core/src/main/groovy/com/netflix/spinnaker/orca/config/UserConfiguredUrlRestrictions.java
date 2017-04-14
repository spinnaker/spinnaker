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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class UserConfiguredUrlRestrictions {
  public static class Builder {
    private String allowedHostnamesRegex = ".*";
    private List<String> allowedSchemes = new ArrayList<>(Arrays.asList("http", "https"));
    private boolean rejectLocalhost = true;
    private boolean rejectLinkLocal = true;

    public String getAllowedHostnamesRegex() {
      return allowedHostnamesRegex;
    }

    public void setAllowedHostnamesRegex(String allowedHostnamesRegex) {
      this.allowedHostnamesRegex = allowedHostnamesRegex;
    }

    public Builder withAllowedHostnamesRegex(String allowedHostnamesRegex) {
      setAllowedHostnamesRegex(allowedHostnamesRegex);
      return this;
    }

    public List<String> getAllowedSchemes() {
      return allowedSchemes;
    }

    public void setAllowedSchemes(List<String> allowedSchemes) {
      this.allowedSchemes = allowedSchemes;
    }

    public Builder withAllowedSchemes(List<String> allowedSchemes) {
      setAllowedSchemes(allowedSchemes);
      return this;
    }

    public boolean isRejectLocalhost() {
      return rejectLocalhost;
    }

    public void setRejectLocalhost(boolean rejectLocalhost) {
      this.rejectLocalhost = rejectLocalhost;
    }

    public Builder withRejectLocalhost(boolean rejectLocalhost) {
      setRejectLocalhost(rejectLocalhost);
      return this;
    }

    public boolean isRejectLinkLocal() {
      return rejectLinkLocal;
    }

    public void setRejectLinkLocal(boolean rejectLinkLocal) {
      this.rejectLinkLocal = rejectLinkLocal;
    }

    public Builder withRejectLinkLocal(boolean rejectLinkLocal) {
      setRejectLinkLocal(rejectLinkLocal);
      return this;
    }

    public UserConfiguredUrlRestrictions build() {
      return new UserConfiguredUrlRestrictions(Pattern.compile(allowedHostnamesRegex), allowedSchemes, rejectLocalhost, rejectLinkLocal);
    }
  }

  private final Pattern allowedHostnames;
  private final Set<String> allowedSchemes;
  private final boolean rejectLocalhost;
  private final boolean rejectLinkLocal;

  public UserConfiguredUrlRestrictions(Pattern allowedHostnames, Collection<String> allowedSchemes, boolean rejectLocalhost, boolean rejectLinkLocal) {
    this.allowedHostnames = allowedHostnames;
    this.allowedSchemes = allowedSchemes == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(allowedSchemes));
    this.rejectLocalhost = rejectLocalhost;
    this.rejectLinkLocal = rejectLinkLocal;
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
        throw new IllegalArgumentException("host not allowed " + u.getHost());
      }

      if (rejectLocalhost || rejectLinkLocal) {
        InetAddress addr = InetAddress.getByName(u.getHost());
        if (rejectLocalhost) {
          if (addr.isLoopbackAddress() || Optional.ofNullable(NetworkInterface.getByInetAddress(addr)).isPresent()) {
            throw new IllegalArgumentException("invalid address for " + u.getHost());
          }
        }
        if (rejectLinkLocal && addr.isLinkLocalAddress()) {
          throw new IllegalArgumentException("invalid address for " + u.getHost());
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
