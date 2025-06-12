/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.selector.SelectableService;
import com.netflix.spinnaker.orca.clouddriver.model.Ami;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.clouddriver.model.ManifestCoordinates;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import java.util.List;
import java.util.Map;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;

public class DelegatingOortService extends DelegatingClouddriverService<OortService>
    implements OortService {

  public DelegatingOortService(SelectableService selectableService) {
    super(selectableService);
  }

  @Override
  public Call<ResponseBody> getCluster(
      String app, String account, String cluster, String cloudProvider) {
    return getService().getCluster(app, account, cluster, cloudProvider);
  }

  @Override
  public Call<Manifest> getManifest(String account, String name, boolean includeEvents) {
    return getService().getManifest(account, name, includeEvents);
  }

  @Override
  public Call<Manifest> getManifest(
      String account, String location, String name, boolean includeEvents) {
    return StringUtils.isEmpty(location)
        ? getService().getManifest(account, name, includeEvents)
        : getService().getManifest(account, location, name, includeEvents);
  }

  @Override
  public Call<ManifestCoordinates> getDynamicManifest(
      String account, String location, String kind, String app, String cluster, String criteria) {
    return getService().getDynamicManifest(account, location, kind, app, cluster, criteria);
  }

  @Override
  public Call<List<ManifestCoordinates>> getClusterManifests(
      String account, String location, String kind, String app, String cluster) {
    return getService().getClusterManifests(account, location, kind, app, cluster);
  }

  @Override
  public Call<ResponseBody> getServerGroupFromCluster(
      String app,
      String account,
      String cluster,
      String serverGroup,
      String region,
      String cloudProvider) {
    return getService()
        .getServerGroupFromCluster(app, account, cluster, serverGroup, region, cloudProvider);
  }

  @Override
  public Call<List<ServerGroup>> getServerGroupsFromClusterTyped(
      String app, String account, String cluster, String serverGroup, String cloudProvider) {
    return getService()
        .getServerGroupsFromClusterTyped(app, account, cluster, serverGroup, cloudProvider);
  }

  @Override
  public Call<List<ServerGroup>> getServerGroups(String app) {
    return getService().getServerGroups(app);
  }

  @Deprecated
  @Override
  public Call<ResponseBody> getServerGroup(
      String app, String account, String region, String serverGroup) {
    return getService().getServerGroup(app, account, region, serverGroup);
  }

  @Override
  public Call<ResponseBody> getServerGroup(String account, String serverGroup, String region) {
    return getService().getServerGroup(account, serverGroup, region);
  }

  @Override
  public Call<ServerGroup> getTargetServerGroup(
      String app,
      String account,
      String cluster,
      String cloudProvider,
      String scope,
      String target) {
    return getService().getTargetServerGroup(app, account, cluster, cloudProvider, scope, target);
  }

  @Override
  public Call<Map<String, Object>> getServerGroupSummary(
      String app,
      String account,
      String cluster,
      String cloudProvider,
      String scope,
      String target,
      String summaryType,
      String onlyEnabled) {
    return getService()
        .getServerGroupSummary(
            app, account, cluster, cloudProvider, scope, target, summaryType, onlyEnabled);
  }

  @Override
  public Call<ResponseBody> getSearchResults(String searchTerm, String type, String cloudProvider) {
    return getService().getSearchResults(searchTerm, type, cloudProvider);
  }

  @Override
  public Call<ResponseBody> getApplication(String app) {
    return getService().getApplication(app);
  }

  @Override
  public Call<ResponseBody> getInstance(String account, String region, String instanceId) {
    return getService().getInstance(account, region, instanceId);
  }

  @Override
  public Call<ResponseBody> fetchArtifact(Artifact artifact) {
    return getService().fetchArtifact(artifact);
  }

  @Override
  public Call<List<Map>> getLoadBalancerDetails(
      String provider, String account, String region, String name) {
    return getService().getLoadBalancerDetails(provider, account, region, name);
  }

  @Override
  public Call<List<Ami>> getByAmiId(String type, String account, String region, Object imageId) {
    return getService().getByAmiId(type, account, region, imageId);
  }

  @Override
  public Call<List<Map>> findImage(
      String cloudProvider, String query, String account, String region, Map additionalFilters) {
    return getService().findImage(cloudProvider, query, account, region, additionalFilters);
  }

  @Override
  public Call<List<Map<String, Object>>> getEntityTags(
      String cloudProvider, String entityType, String entityId, String account, String region) {
    return getService().getEntityTags(cloudProvider, entityType, entityId, account, region);
  }

  @Override
  public Call<List<Map>> getEntityTags(Map parameters) {
    return getService().getEntityTags(parameters);
  }

  @Override
  public Call<Map> getCloudFormationStack(String stackId) {
    return getService().getCloudFormationStack(stackId);
  }

  @Override
  public Call<Map<String, Object>> getServiceInstance(
      String account, String cloudProvider, String region, String serviceInstanceName) {
    return getService().getServiceInstance(account, cloudProvider, region, serviceInstanceName);
  }

  @Override
  public Call<List<Map<String, Object>>> getCredentials(boolean expand) {
    return getService().getCredentials(expand);
  }
}
