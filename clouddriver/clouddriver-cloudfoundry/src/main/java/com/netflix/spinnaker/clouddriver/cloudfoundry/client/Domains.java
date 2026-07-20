/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.collectPages;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.DomainService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Domain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import groovy.util.logging.Slf4j;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Slf4j
public class Domains {
  private final DomainService api;
  private final Organizations organizations;

  private final LoadingCache<String, CloudFoundryDomain> domainCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .build(
              new CacheLoader<String, CloudFoundryDomain>() {
                @Override
                public CloudFoundryDomain load(@Nonnull String guid)
                    throws CloudFoundryApiException, ResourceNotFoundException {
                  Domain domain = safelyCall(() -> api.findById(guid)).orElse(null);

                  if (domain == null) throw new ResourceNotFoundException();

                  return map(domain);
                }
              });

  private CloudFoundryDomain map(Domain domain) throws CloudFoundryApiException {
    String orgGuid = domain.getOwningOrganizationGuid();
    return CloudFoundryDomain.builder()
        .id(domain.getGuid())
        .name(domain.getName())
        .organization(orgGuid != null ? organizations.findById(orgGuid) : null)
        .build();
  }

  @Nullable
  public CloudFoundryDomain findById(String guid) throws CloudFoundryApiException {
    try {
      return domainCache.get(guid);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ResourceNotFoundException) return null;
      throw new CloudFoundryApiException(e.getCause(), "Unable to find domain by id");
    }
  }

  public Optional<CloudFoundryDomain> findByName(String domainName)
      throws CloudFoundryApiException {
    return all().stream().filter(d -> d.getName().equals(domainName)).findFirst();
  }

  public List<CloudFoundryDomain> all() throws CloudFoundryApiException {
    List<Domain> allDomains = collectPages("domains", page -> api.all(page));

    List<CloudFoundryDomain> domains = new ArrayList<>(allDomains.size());
    for (Domain domain : allDomains) {
      domains.add(map(domain));
    }
    for (CloudFoundryDomain domain : domains) {
      domainCache.put(domain.getId(), domain);
    }
    return domains;
  }

  public CloudFoundryDomain getDefault() {
    return map(
        safelyCall(() -> api.all(null))
            .orElseThrow(() -> new CloudFoundryApiException("Unable to retrieve default domain"))
            .getResources()
            .iterator()
            .next());
  }
}
