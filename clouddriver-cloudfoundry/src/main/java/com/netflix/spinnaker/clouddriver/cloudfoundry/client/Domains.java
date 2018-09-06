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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.DomainService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Domain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Resource;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import groovy.util.logging.Slf4j;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.collectPageResources;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;

@RequiredArgsConstructor
@Slf4j
public class Domains {
  private final DomainService api;
  private final Organizations organizations;

  private final LoadingCache<String, CloudFoundryDomain> domainCache = CacheBuilder.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build(new CacheLoader<String, CloudFoundryDomain>() {
      @Override
      public CloudFoundryDomain load(@Nonnull String guid) throws CloudFoundryApiException, ResourceNotFoundException {
        Resource<Domain> domain = safelyCall(() -> api.findSharedDomainById(guid))
          .orElseGet(() -> safelyCall(() -> api.findPrivateDomainById(guid)).orElse(null));

        if (domain == null)
          throw new ResourceNotFoundException();

        return map(domain);
      }
    });

  private CloudFoundryDomain map(Resource<Domain> res) throws CloudFoundryApiException {
    String orgGuid = res.getEntity().getOwningOrganizationGuid();
    return CloudFoundryDomain.builder()
      .id(res.getMetadata().getGuid())
      .name(res.getEntity().getName())
      .organization(orgGuid != null ? organizations.findById(orgGuid) : null)
      .build();
  }

  @Nullable
  public CloudFoundryDomain findById(String guid) throws CloudFoundryApiException {
    try {
      return domainCache.get(guid);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ResourceNotFoundException)
        return null;
      throw new CloudFoundryApiException(e.getCause(), "Unable to find domain by id");
    }
  }

  @Nullable
  public CloudFoundryDomain findByName(String domainName) throws CloudFoundryApiException {
    return all().stream().filter(d -> d.getName().equals(domainName)).findFirst().orElse(null);
  }

  public List<CloudFoundryDomain> all() throws CloudFoundryApiException {
    List<Resource<Domain>> sharedDomains = collectPageResources("shared domains", api::allShared);
    List<Resource<Domain>> privateDomains = collectPageResources("private domains", api::allPrivate);

    List<CloudFoundryDomain> domains = new ArrayList<>(sharedDomains.size() + privateDomains.size());
    for (Resource<Domain> sharedDomain : sharedDomains) {
      domains.add(map(sharedDomain));
    }
    for (Resource<Domain> privateDomain : privateDomains) {
      domains.add(map(privateDomain));
    }
    for (CloudFoundryDomain domain : domains) {
      domainCache.put(domain.getId(), domain);
    }
    return domains;
  }
}
