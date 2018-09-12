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

package com.netflix.spinnaker.clouddriver.cloudfoundry.cache;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.*;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys.Namespace.*;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

@Repository
public class CacheRepository {
  private final ObjectMapper objectMapper = new ObjectMapper()
    .disable(MapperFeature.DEFAULT_VIEW_INCLUSION);

  private final Cache cacheView;

  public CacheRepository(Cache cacheView) {
    this.cacheView = cacheView;
    this.objectMapper
      .setConfig(objectMapper.getSerializationConfig().withView(Views.Cache.class))
      .setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
        @Override
        public JsonPOJOBuilder.Value findPOJOBuilderConfig(AnnotatedClass ac) {
          return new JsonPOJOBuilder.Value("build", "");
        }
      });
  }

  public Set<CloudFoundryApplication> findApplicationsByKeys(Collection<String> keys, Detail detail) {
    return cacheView.getAll(APPLICATIONS.getNs(), keys, detail.appFilter()).stream()
      .map(appData -> applicationFromCacheData(appData, detail))
      .collect(toSet());
  }

  public Optional<CloudFoundryApplication> findApplicationByKey(String key, Detail detail) {
    return Optional.ofNullable(cacheView.get(APPLICATIONS.getNs(), key, detail.appFilter()))
      .map(appData -> applicationFromCacheData(appData, detail));
  }

  private CloudFoundryApplication applicationFromCacheData(CacheData appData, Detail detail) {
    CloudFoundryApplication app = objectMapper.convertValue(appData.getAttributes().get("resource"), CloudFoundryApplication.class);
    if (detail.equals(Detail.NONE)) {
      return app.withClusters(emptySet());
    }
    return app.withClusters(findClustersByKeys(appData.getRelationships().get(CLUSTERS.getNs()), Detail.NONE));
  }

  public Set<CloudFoundryCluster> findClustersByKeys(Collection<String> keys, Detail detail) {
    return cacheView.getAll(CLUSTERS.getNs(), keys, detail.clusterFilter()).stream()
      .map(clusterData -> clusterFromCacheData(clusterData, detail))
      .collect(toSet());
  }

  public Optional<CloudFoundryCluster> findClusterByKey(String key, Detail detail) {
    return Optional.ofNullable(cacheView.get(CLUSTERS.getNs(), key, detail.clusterFilter()))
      .map(clusterData -> clusterFromCacheData(clusterData, detail));
  }

  private CloudFoundryCluster clusterFromCacheData(CacheData clusterData, Detail detail) {
    CloudFoundryCluster cluster = objectMapper.convertValue(clusterData.getAttributes().get("resource"), CloudFoundryCluster.class);
    if (detail.equals(Detail.NONE)) {
      return cluster.withServerGroups(emptySet());
    }
    return cluster.withServerGroups(findServerGroupsByKeys(clusterData.getRelationships().get(SERVER_GROUPS.getNs()), detail.deep()));
  }

  public Set<CloudFoundryServerGroup> findServerGroupsByKeys(Collection<String> keys, Detail detail) {
    return cacheView.getAll(SERVER_GROUPS.getNs(), keys, detail.serverGroupFilter()).stream()
      .map(serverGroupData -> serverGroupFromCacheData(serverGroupData, detail))
      .collect(toSet());
  }

  public Optional<CloudFoundryServerGroup> findServerGroupByKey(String key, Detail detail) {
    return Optional.ofNullable(cacheView.get(SERVER_GROUPS.getNs(), key, detail.serverGroupFilter()))
      .map(serverGroupData -> serverGroupFromCacheData(serverGroupData, detail));
  }

  private CloudFoundryServerGroup serverGroupFromCacheData(CacheData serverGroupData, Detail detail) {
    CloudFoundryServerGroup serverGroup = objectMapper.convertValue(serverGroupData.getAttributes().get("resource"), CloudFoundryServerGroup.class);
    if (detail.equals(Detail.NONE)) {
      return serverGroup.withLoadBalancerNames(emptySet()).withInstances(emptySet());
    }
    return serverGroup
      .withLoadBalancerNames(findLoadBalancersByKeys(serverGroupData.getRelationships().get(LOAD_BALANCERS.getNs()), Detail.NONE).stream()
        .map(CloudFoundryLoadBalancer::getName)
        .collect(toSet()))
      .withInstances(findInstancesByKeys(serverGroupData.getRelationships().get(INSTANCES.getNs())));
  }

  public Set<CloudFoundryLoadBalancer> findLoadBalancersByKeys(Collection<String> keys, Detail detail) {
    return cacheView.getAll(LOAD_BALANCERS.getNs(), keys, detail.loadBalancerFilter()).stream()
      .map(lbData -> loadBalancerFromCacheData(lbData, detail))
      .collect(toSet());
  }

  private CloudFoundryLoadBalancer loadBalancerFromCacheData(CacheData lbData, Detail detail) {
    CloudFoundryLoadBalancer loadBalancer = objectMapper.convertValue(lbData.getAttributes().get("resource"), CloudFoundryLoadBalancer.class);
    if (detail.equals(Detail.NONE)) {
      return loadBalancer;
    }

    // the server groups populated here will have an empty load balancer names set to avoid a cyclic call back to findLoadBalancersByKeys
    return loadBalancer.withMappedApps(findServerGroupsByKeys(lbData.getRelationships().get(SERVER_GROUPS.getNs()), Detail.NONE));
  }

  public Set<CloudFoundryInstance> findInstancesByKeys(Collection<String> keys) {
    return cacheView.getAll(INSTANCES.getNs(), keys).stream()
      .map(instanceData -> objectMapper.convertValue(instanceData.getAttributes().get("resource"), CloudFoundryInstance.class))
      .collect(toSet());
  }

  public Optional<CloudFoundryInstance> findInstanceByKey(String key) {
    return Optional.ofNullable(cacheView.get(INSTANCES.getNs(), key))
      .map(instanceData -> objectMapper.convertValue(instanceData.getAttributes().get("resource"), CloudFoundryInstance.class));
  }

  public enum Detail {
    /**
     * Don't deserialize any relationships.
     */
    NONE,

    /**
     * Only deserialize names.
     */
    NAMES_ONLY,

    /**
     * Fully rehydrate the model.
     */
    FULL;

    public Detail deep() {
      switch (this) {
        case FULL:
          return FULL;
        case NAMES_ONLY:
        case NONE:
        default:
          return NONE;
      }
    }

    public RelationshipCacheFilter appFilter() {
      switch (this) {
        case FULL:
        case NAMES_ONLY:
          return RelationshipCacheFilter.include(CLUSTERS.getNs());
        case NONE:
        default:
          return RelationshipCacheFilter.none();
      }
    }

    public RelationshipCacheFilter clusterFilter() {
      switch (this) {
        case FULL:
        case NAMES_ONLY:
          return RelationshipCacheFilter.include(SERVER_GROUPS.getNs());
        case NONE:
        default:
          return RelationshipCacheFilter.none();
      }
    }

    public RelationshipCacheFilter serverGroupFilter() {
      switch (this) {
        case FULL:
        case NAMES_ONLY:
          return RelationshipCacheFilter.include(INSTANCES.getNs(), LOAD_BALANCERS.getNs());
        case NONE:
        default:
          // we always populate instance data on server groups, regardless of detail level
          return RelationshipCacheFilter.include(INSTANCES.getNs());
      }
    }

    public RelationshipCacheFilter loadBalancerFilter() {
      switch (this) {
        case FULL:
        case NAMES_ONLY:
          return RelationshipCacheFilter.include(SERVER_GROUPS.getNs());
        case NONE:
        default:
          return RelationshipCacheFilter.none();
      }
    }
  }
}
