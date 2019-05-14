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

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.OrganizationService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Organizations {
  private final OrganizationService api;

  private final LoadingCache<String, CloudFoundryOrganization> organizationCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .build(
              new CacheLoader<String, CloudFoundryOrganization>() {
                @Override
                public CloudFoundryOrganization load(@Nonnull String guid)
                    throws CloudFoundryApiException, ResourceNotFoundException {
                  return safelyCall(() -> api.findById(guid))
                      .map(
                          org ->
                              CloudFoundryOrganization.builder()
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
      if (e.getCause() instanceof ResourceNotFoundException) return null;
      throw new CloudFoundryApiException(e.getCause(), "Unable to find organization by id");
    }
  }

  public Optional<CloudFoundryOrganization> findByName(String orgName)
      throws CloudFoundryApiException {
    return safelyCall(() -> api.all(null, Collections.singletonList("name:" + orgName)))
        .flatMap(
            page ->
                page.getResources().stream()
                    .findAny()
                    .map(
                        org ->
                            CloudFoundryOrganization.builder()
                                .id(org.getMetadata().getGuid())
                                .name(org.getEntity().getName())
                                .build()));
  }

  public Optional<CloudFoundrySpace> findSpaceByRegion(String region) {
    CloudFoundrySpace space = CloudFoundrySpace.fromRegion(region);

    // fully populates the space guid which is what Cloud Foundry's API expects as an input, not the
    // name.
    Optional<CloudFoundryOrganization> organization = findByName(space.getOrganization().getName());

    Optional<CloudFoundrySpace> spaceOptional =
        organization.map(org -> findSpaceByName(org, space.getName()));

    spaceOptional.ifPresent(
        spaceCase -> {
          if (!(space.getName().equals(spaceCase.getName())
              && space.getOrganization().getName().equals(spaceCase.getOrganization().getName()))) {
            throw new CloudFoundryApiException("Org or Space name not in correct case");
          }
        });

    return spaceOptional;
  }

  @Nullable
  private CloudFoundrySpace findSpaceByName(
      CloudFoundryOrganization organization, String spaceName) {
    return safelyCall(
            () ->
                api.getSpaceByName(
                    organization.getId(), null, Collections.singletonList("name:" + spaceName)))
        .flatMap(
            page ->
                page.getResources().stream()
                    .findAny()
                    .map(
                        resource ->
                            CloudFoundrySpace.builder()
                                .id(resource.getMetadata().getGuid())
                                .name(resource.getEntity().getName())
                                .organization(organization)
                                .build()))
        .orElse(null);
  }
}
