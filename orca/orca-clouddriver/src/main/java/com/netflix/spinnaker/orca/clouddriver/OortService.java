/*
 * Copyright 2014 Netflix, Inc.
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
import com.netflix.spinnaker.orca.clouddriver.model.Ami;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.clouddriver.model.ManifestCoordinates;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import java.util.List;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface OortService {
  @GET("/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}")
  Call<ResponseBody> getCluster(
      @Path("app") String app,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("cloudProvider") String cloudProvider);

  @GET("/applications/{app}/serverGroups")
  Call<List<ServerGroup>> getServerGroups(@Path("app") String app);

  @GET(
      "/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/serverGroups/{serverGroup}")
  Call<ResponseBody> getServerGroupFromCluster(
      @Path("app") String app,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("serverGroup") String serverGroup,
      @Query("region") String region,
      @Path("cloudProvider") String cloudProvider);

  @GET(
      "/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/serverGroups/{serverGroup}")
  Call<List<ServerGroup>> getServerGroupsFromClusterTyped(
      @Path("app") String app,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("serverGroup") String serverGroup,
      @Path("cloudProvider") String cloudProvider);

  @GET("/manifests/{account}/_/{manifest}")
  Call<Manifest> getManifest(
      @Path("account") String account,
      @Path("manifest") String manifest,
      @Query("includeEvents") boolean includeEvents);

  @GET("/manifests/{account}/{location}/{manifest}")
  Call<Manifest> getManifest(
      @Path("account") String account,
      @Path("location") String location,
      @Path("manifest") String manifest,
      @Query("includeEvents") boolean includeEvents);

  @GET("/manifests/{account}/{location}/{kind}/cluster/{app}/{clusterName}/dynamic/{criteria}")
  Call<ManifestCoordinates> getDynamicManifest(
      @Path("account") String account,
      @Path("location") String location,
      @Path("kind") String kind,
      @Path("app") String app,
      @Path("clusterName") String clusterName,
      @Path("criteria") String criteria);

  @GET("/manifests/{account}/{location}/{kind}/cluster/{app}/{clusterName}")
  Call<List<ManifestCoordinates>> getClusterManifests(
      @Path("account") String account,
      @Path("location") String location,
      @Path("kind") String kind,
      @Path("app") String app,
      @Path("clusterName") String clusterName);

  @Deprecated
  @GET("/applications/{app}/serverGroups/{account}/{region}/{serverGroup}")
  Call<ResponseBody> getServerGroup(
      @Path("app") String app,
      @Path("account") String account,
      @Path("region") String region,
      @Path("serverGroup") String serverGroup);

  @GET("/serverGroups/{account}/{region}/{serverGroup}")
  Call<ResponseBody> getServerGroup(
      @Path("account") String account,
      @Path("region") String region,
      @Path("serverGroup") String serverGroup);

  @GET(
      "/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{scope}/serverGroups/target/{target}")
  Call<ServerGroup> getTargetServerGroup(
      @Path("app") String app,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("cloudProvider") String cloudProvider,
      @Path("scope") String scope,
      @Path("target") String target);

  @GET(
      "/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{scope}/serverGroups/target/{target}/{summaryType}")
  Call<Map<String, Object>> getServerGroupSummary(
      @Path("app") String app,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("cloudProvider") String cloudProvider,
      @Path("scope") String scope,
      @Path("target") String target,
      @Path("summaryType") String summaryType,
      @Query("onlyEnabled") String onlyEnabled);

  @GET("/search")
  Call<ResponseBody> getSearchResults(
      @Query("q") String searchTerm,
      @Query("type") String type,
      @Query("cloudProvider") String cloudProvider);

  @GET("/applications/{app}")
  Call<ResponseBody> getApplication(@Path("app") String app);

  @GET("/instances/{account}/{region}/{instanceId}")
  Call<ResponseBody> getInstance(
      @Path("account") String account,
      @Path("region") String region,
      @Path("instanceId") String instanceId);

  @PUT("/artifacts/fetch/")
  Call<ResponseBody> fetchArtifact(@Body Artifact artifact);

  @GET("/{provider}/loadBalancers/{account}/{region}/{name}")
  Call<List<Map>> getLoadBalancerDetails(
      @Path("provider") String provider,
      @Path("account") String account,
      @Path("region") String region,
      @Path("name") String name);

  @GET("/{type}/images/{account}/{region}/{imageId}")
  Call<List<Ami>> getByAmiId(
      @Path("type") String type,
      @Path("account") String account,
      @Path("region") String region,
      @Path("imageId") Object imageId);

  @GET("/{cloudProvider}/images/find")
  Call<List<Map>> findImage(
      @Path("cloudProvider") String cloudProvider,
      @Query("q") String query,
      @Query("account") String account,
      @Query("region") String region,
      @QueryMap Map<String, String> additionalFilters);

  @GET("/tags")
  Call<List<Map<String, Object>>> getEntityTags(
      @Query("cloudProvider") String cloudProvider,
      @Query("entityType") String entityType,
      @Query("entityId") String entityId,
      @Query("account") String account,
      @Query("region") String region);

  @GET("/tags")
  Call<List<Map>> getEntityTags(@QueryMap Map parameters);

  @GET("/aws/cloudFormation/stacks/{stackId}")
  Call<Map> getCloudFormationStack(@Path(value = "stackId", encoded = true) String stackId);

  @GET("/servicebroker/{account}/serviceInstance")
  Call<Map<String, Object>> getServiceInstance(
      @Path("account") String account,
      @Query("cloudProvider") String cloudProvider,
      @Query("region") String region,
      @Query("serviceInstanceName") String serviceInstanceName);

  @GET("/credentials")
  Call<List<Map<String, Object>>> getCredentials(@Query("expand") boolean expand);
}
