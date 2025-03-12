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

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.*;
import static java.util.stream.Collectors.toList;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.SpaceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Space;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Spaces {
  private final SpaceService api;
  private final Organizations organizations;

  private final LoadingCache<String, CloudFoundrySpace> spaceCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .build(
              new CacheLoader<String, CloudFoundrySpace>() {
                @Override
                public CloudFoundrySpace load(@Nonnull String guid)
                    throws CloudFoundryApiException, ResourceNotFoundException {
                  return safelyCall(() -> api.findById(guid))
                      .map(Spaces.this::map)
                      .orElseThrow(ResourceNotFoundException::new);
                }
              });

  public CloudFoundrySpace findById(String guid) throws CloudFoundryApiException {
    try {
      return spaceCache.get(guid);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ResourceNotFoundException) return null;
      throw new CloudFoundryApiException(e.getCause(), "Unable to find space by id");
    }
  }

  public List<CloudFoundrySpace> all() throws CloudFoundryApiException {
    return collectPages("spaces", page -> api.all(page, null, null)).stream()
        .map(this::map)
        .collect(toList());
  }

  @Nullable
  public CloudFoundryServiceInstance getServiceInstanceById(
      String spaceId, String serviceInstanceName) {
    return collectPageResources(
            "get service instances by id",
            pg ->
                api.getServiceInstancesById(
                    spaceId, pg, Collections.singletonList("name:" + serviceInstanceName)))
        .stream()
        .findFirst()
        .map(
            e ->
                CloudFoundryServiceInstance.builder()
                    .name(e.getEntity().getName())
                    .id(e.getMetadata().getGuid())
                    .build())
        .orElse(null);
  }

  @Nullable
  public CloudFoundrySpace findByName(String orgId, String spaceName)
      throws CloudFoundryApiException {
    return collectPages("spaces", page -> api.all(page, spaceName, orgId)).stream()
        .findAny()
        .map(this::map)
        .orElse(null);
  }

  @Nullable
  public CloudFoundryServiceInstance getServiceInstanceByNameAndSpace(
      String serviceInstanceName, CloudFoundrySpace space) {
    return Optional.ofNullable(getServiceInstanceById(space.getId(), serviceInstanceName))
        .orElse(null);
  }

  private CloudFoundrySpace map(Space space) throws CloudFoundryApiException {
    return CloudFoundrySpace.builder()
        .id(space.getGuid())
        .name(space.getName())
        .organization(
            organizations.findById(
                space.getRelationships().get("organization").getData().getGuid()))
        .build();
  }

  public Optional<CloudFoundrySpace> findSpaceByRegion(String region) {
    CloudFoundrySpace space = CloudFoundrySpace.fromRegion(region);

    CloudFoundryOrganization organization =
        organizations
            .findByName(space.getOrganization().getName())
            .orElseThrow(
                () ->
                    new CloudFoundryApiException(
                        "Unable to find organization: " + space.getOrganization().getName()));

    Optional<CloudFoundrySpace> spaceOptional =
        collectPages("spaces", page -> api.all(page, space.getName(), organization.getId()))
            .stream()
            .findAny()
            .map(
                s ->
                    CloudFoundrySpace.builder()
                        .id(s.getGuid())
                        .name(s.getName())
                        .organization(organization)
                        .build());

    spaceOptional.ifPresent(
        spaceCase -> {
          if (!(space.getName().equals(spaceCase.getName())
              && space.getOrganization().getName().equals(spaceCase.getOrganization().getName()))) {
            throw new CloudFoundryApiException("Org or Space name not in correct case");
          }
        });

    return spaceOptional;
  }

  public List<CloudFoundrySpace> findAllBySpaceNamesAndOrgNames(
      List<String> spaceNames, List<String> orgNames) {
    Map<String, CloudFoundryOrganization> allOrgsByGuids = new HashMap<>();
    organizations.findAllByNames(orgNames).stream().forEach(o -> allOrgsByGuids.put(o.getId(), o));

    String spaceNamesQ =
        spaceNames == null || spaceNames.isEmpty() ? null : String.join(",", spaceNames);
    String orgGuidsQ =
        allOrgsByGuids.keySet().isEmpty() ? null : String.join(",", allOrgsByGuids.keySet());

    return collectPages("spaces", page -> api.all(page, spaceNamesQ, orgGuidsQ)).stream()
        .map(
            s ->
                CloudFoundrySpace.builder()
                    .organization(
                        allOrgsByGuids.getOrDefault(
                            s.getRelationships().get("organization").getData().getGuid(), null))
                    .name(s.getName())
                    .id(s.getGuid())
                    .build())
        .filter(
            s -> s.getOrganization() != null && orgNames.contains(s.getOrganization().getName()))
        .collect(toList());
  }
}
