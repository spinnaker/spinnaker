/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.dynomite;

import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import com.netflix.dyno.connectionpool.impl.lb.HostToken;
import java.util.List;
import java.util.Set;

public class StaticTokenMapSupplier implements TokenMapSupplier {

  private final List<HostToken> hostTokens;

  public StaticTokenMapSupplier(List<HostToken> hostTokens) {
    this.hostTokens = hostTokens;
  }

  @Override
  public List<HostToken> getTokens(Set<Host> activeHosts) {
    return hostTokens;
  }

  @Override
  public HostToken getTokenForHost(final Host host, final Set<Host> activeHosts) {
    for (HostToken hostToken : hostTokens) {
      if (hostToken.getHost().compareTo(host) == 0) {
        return hostToken;
      }
    }
    return null;
  }
}
