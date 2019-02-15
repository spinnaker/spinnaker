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
import com.netflix.spinnaker.orca.clouddriver.config.SelectableService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import org.apache.commons.lang.StringUtils;
import retrofit.client.Response;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DelegatingOortService
  extends DelegatingClouddriverService<OortService>
  implements OortService {

  public DelegatingOortService(SelectableService selectableService) {
    super(selectableService);
  }

  @Override
  public Response getCluster(String app, String account, String cluster, String cloudProvider) {
    return getService().getCluster(app, account, cluster, cloudProvider);
  }

  @Override
  public Manifest getManifest(String account, String name) {
    return getService().getManifest(account, name);
  }

  @Override
  public Manifest getManifest(String account, String location, String name) {
    return StringUtils.isEmpty(location) ? getService().getManifest(account, name) : getService().getManifest(account, location, name);
  }

  @Override
  public Manifest getDynamicManifest(String account, String location, String kind, String app, String cluster, String criteria) {
    return getService().getDynamicManifest(account, location, kind, app, cluster, criteria);
  }

  @Override
  public Response getServerGroupFromCluster(String app, String account, String cluster, String serverGroup, String region, String cloudProvider) {
    return getService().getServerGroupFromCluster(app, account, cluster, serverGroup, region, cloudProvider);
  }

  @Override
  public Response getServerGroups(String app) {
    return getService().getServerGroups(app);
  }

  @Deprecated
  @Override
  public Response getServerGroup(String app, String account, String region, String serverGroup) {
    return getService().getServerGroup(app, account, region, serverGroup);
  }

  @Override
  public Response getServerGroup(String account, String serverGroup, String region) {
    return getService().getServerGroup(account, serverGroup, region);
  }

  @Override
  public Response getTargetServerGroup(String app, String account, String cluster, String cloudProvider, String scope, String target) {
    return getService().getTargetServerGroup(app, account, cluster, cloudProvider, scope, target);
  }

  @Override
  public Map<String, Object> getServerGroupSummary(String app, String account, String cluster, String cloudProvider, String scope, String target, String summaryType, String onlyEnabled) {
    return getService().getServerGroupSummary(app, account, cluster, cloudProvider, scope, target, summaryType, onlyEnabled);
  }

  @Override
  public Response getSearchResults(String searchTerm, String type, String cloudProvider) {
    return getService().getSearchResults(searchTerm, type, cloudProvider);
  }

  @Override
  public Response getApplication(String app) {
    return getService().getApplication(app);
  }

  @Override
  public Response getInstance(String account, String region, String instanceId) {
    return getService().getInstance(account, region, instanceId);
  }

  @Override
  public Response fetchArtifact(Artifact artifact) {
    return getService().fetchArtifact(artifact);
  }

  @Override
  public List<Map> getLoadBalancerDetails(String provider, String account, String region, String name) {
    return getService().getLoadBalancerDetails(provider, account, region, name);
  }

  @Override
  public List<Map> getByAmiId(String type, String account, String region, Object imageId) {
    return getService().getByAmiId(type, account, region, imageId);
  }

  @Override
  public List<Map> findImage(String cloudProvider, String query, String account, String region, Map additionalFilters) {
    return getService().findImage(cloudProvider, query, account, region, additionalFilters);
  }

  @Override
  public List<Map> getEntityTags(String cloudProvider, String entityType, String entityId, String account, String region) {
    return getService().getEntityTags(cloudProvider, entityType, entityId, account, region);
  }

  @Override
  public List<Map> getEntityTags(Map parameters) {
    return getService().getEntityTags(parameters);
  }

  @Override
  public Map getCloudFormationStack(String stackId) {
    return getService().getCloudFormationStack(stackId);
  }
}
