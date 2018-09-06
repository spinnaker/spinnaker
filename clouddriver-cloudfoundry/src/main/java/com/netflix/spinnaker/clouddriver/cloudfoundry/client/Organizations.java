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
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.OrganizationService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Organization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Page;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;

@RequiredArgsConstructor
public class Organizations {
  private final OrganizationService api;

  private final LoadingCache<String, CloudFoundryOrganization> organizationCache = CacheBuilder.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build(new CacheLoader<String, CloudFoundryOrganization>() {
      @Override
      public CloudFoundryOrganization load(@Nonnull String guid) throws CloudFoundryApiException, ResourceNotFoundException {
        return safelyCall(() -> api.findById(guid))
          .map(org -> CloudFoundryOrganization.builder()
            .id(org.getMetadata().getGuid())
            .name(org.getEntity().getName())
            .build())
          .orElseThrow(ResourceNotFoundException::new);
      }
    });

  @Nullable
  public CloudFoundryOrganization findById(String orgId) throws CloudFoundryApiException {
    try {
      return organizationCache.get(orgId);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ResourceNotFoundException)
        return null;
      throw new CloudFoundryApiException(e.getCause(), "Unable to find organization by id");
    }
  }

  @Nullable
  public CloudFoundryOrganization findByName(String orgName) throws CloudFoundryApiException {
    Page<Organization> page = safelyCall(() -> api.all(null, Collections.singletonList("name:" + orgName))).orElse(null);

    if (page == null)
      return null;

    return page.getResources().stream().findAny()
      .map(org -> CloudFoundryOrganization.builder()
        .id(org.getMetadata().getGuid())
        .name(org.getEntity().getName())
        .build())
      .orElse(null);
  }
}
