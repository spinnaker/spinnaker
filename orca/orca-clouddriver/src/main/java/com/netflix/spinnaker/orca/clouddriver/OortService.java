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
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.PUT;
import retrofit.http.Path;
import retrofit.http.Query;
import retrofit.http.QueryMap;

public interface OortService {
<<<<<<< HEAD
  @GET("/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}")
  Response getCluster(
=======
  @GET("applications/{app}/clusters/{account}/{cluster}/{cloudProvider}")
  Call<ResponseBody> getCluster(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("app") String app,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("cloudProvider") String cloudProvider);

<<<<<<< HEAD
  @GET("/applications/{app}/serverGroups")
  Response getServerGroups(@Path("app") String app);

  @GET(
      "/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/serverGroups/{serverGroup}")
  Response getServerGroupFromCluster(
=======
  @GET("applications/{app}/serverGroups")
  Call<List<ServerGroup>> getServerGroups(@Path("app") String app);

  @GET("applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/serverGroups/{serverGroup}")
  Call<ResponseBody> getServerGroupFromCluster(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("app") String app,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("serverGroup") String serverGroup,
      @Query("region") String region,
      @Path("cloudProvider") String cloudProvider);

<<<<<<< HEAD
  @GET(
      "/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/serverGroups/{serverGroup}")
  List<ServerGroup> getServerGroupsFromClusterTyped(
=======
  @GET("applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/serverGroups/{serverGroup}")
  Call<List<ServerGroup>> getServerGroupsFromClusterTyped(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("app") String app,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("serverGroup") String serverGroup,
      @Path("cloudProvider") String cloudProvider);

<<<<<<< HEAD
  @GET("/manifests/{account}/_/{manifest}")
  Manifest getManifest(
=======
  @GET("manifests/{account}/_/{manifest}")
  Call<Manifest> getManifest(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("account") String account,
      @Path("manifest") String manifest,
      @Query("includeEvents") boolean includeEvents);

<<<<<<< HEAD
  @GET("/manifests/{account}/{location}/{manifest}")
  Manifest getManifest(
=======
  @GET("manifests/{account}/{location}/{manifest}")
  Call<Manifest> getManifest(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("account") String account,
      @Path("location") String location,
      @Path("manifest") String manifest,
      @Query("includeEvents") boolean includeEvents);

<<<<<<< HEAD
  @GET("/manifests/{account}/{location}/{kind}/cluster/{app}/{clusterName}/dynamic/{criteria}")
  ManifestCoordinates getDynamicManifest(
=======
  @GET("manifests/{account}/{location}/{kind}/cluster/{app}/{clusterName}/dynamic/{criteria}")
  Call<ManifestCoordinates> getDynamicManifest(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("account") String account,
      @Path("location") String location,
      @Path("kind") String kind,
      @Path("app") String app,
      @Path("clusterName") String clusterName,
      @Path("criteria") String criteria);

<<<<<<< HEAD
  @GET("/manifests/{account}/{location}/{kind}/cluster/{app}/{clusterName}")
  List<ManifestCoordinates> getClusterManifests(
=======
  @GET("manifests/{account}/{location}/{kind}/cluster/{app}/{clusterName}")
  Call<List<ManifestCoordinates>> getClusterManifests(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("account") String account,
      @Path("location") String location,
      @Path("kind") String kind,
      @Path("app") String app,
      @Path("clusterName") String clusterName);

  @Deprecated
<<<<<<< HEAD
  @GET("/applications/{app}/serverGroups/{account}/{region}/{serverGroup}")
  Response getServerGroup(
=======
  @GET("applications/{app}/serverGroups/{account}/{region}/{serverGroup}")
  Call<ResponseBody> getServerGroup(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("app") String app,
      @Path("account") String account,
      @Path("region") String region,
      @Path("serverGroup") String serverGroup);

<<<<<<< HEAD
  @GET("/serverGroups/{account}/{region}/{serverGroup}")
  Response getServerGroup(
=======
  @GET("serverGroups/{account}/{region}/{serverGroup}")
  Call<ResponseBody> getServerGroup(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("account") String account,
      @Path("region") String region,
      @Path("serverGroup") String serverGroup);

  @GET(
<<<<<<< HEAD
      "/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{scope}/serverGroups/target/{target}")
  ServerGroup getTargetServerGroup(
=======
      "applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{scope}/serverGroups/target/{target}")
  Call<ServerGroup> getTargetServerGroup(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("app") String app,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("cloudProvider") String cloudProvider,
      @Path("scope") String scope,
      @Path("target") String target);

  @GET(
<<<<<<< HEAD
      "/applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{scope}/serverGroups/target/{target}/{summaryType}")
  Map<String, Object> getServerGroupSummary(
=======
      "applications/{app}/clusters/{account}/{cluster}/{cloudProvider}/{scope}/serverGroups/target/{target}/{summaryType}")
  Call<Map<String, Object>> getServerGroupSummary(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("app") String app,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("cloudProvider") String cloudProvider,
      @Path("scope") String scope,
      @Path("target") String target,
      @Path("summaryType") String summaryType,
      @Query("onlyEnabled") String onlyEnabled);

<<<<<<< HEAD
  @GET("/search")
  Response getSearchResults(
=======
  @GET("search")
  Call<ResponseBody> getSearchResults(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Query("q") String searchTerm,
      @Query("type") String type,
      @Query("cloudProvider") String cloudProvider);

<<<<<<< HEAD
  @GET("/applications/{app}")
  Response getApplication(@Path("app") String app);

  @GET("/instances/{account}/{region}/{instanceId}")
  Response getInstance(
=======
  @GET("applications/{app}")
  Call<ResponseBody> getApplication(@Path("app") String app);

  @GET("instances/{account}/{region}/{instanceId}")
  Call<ResponseBody> getInstance(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("account") String account,
      @Path("region") String region,
      @Path("instanceId") String instanceId);

<<<<<<< HEAD
  @PUT("/artifacts/fetch/")
  Response fetchArtifact(@Body Artifact artifact);

  @GET("/{provider}/loadBalancers/{account}/{region}/{name}")
  List<Map> getLoadBalancerDetails(
=======
  @PUT("artifacts/fetch/")
  Call<ResponseBody> fetchArtifact(@Body Artifact artifact);

  @GET("{provider}/loadBalancers/{account}/{region}/{name}")
  Call<List<Map>> getLoadBalancerDetails(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("provider") String provider,
      @Path("account") String account,
      @Path("region") String region,
      @Path("name") String name);

<<<<<<< HEAD
  @GET("/{type}/images/{account}/{region}/{imageId}")
  List<Ami> getByAmiId(
=======
  @GET("{type}/images/{account}/{region}/{imageId}")
  Call<List<Ami>> getByAmiId(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("type") String type,
      @Path("account") String account,
      @Path("region") String region,
      @Path("imageId") Object imageId);

<<<<<<< HEAD
  @GET("/{cloudProvider}/images/find")
  List<Map> findImage(
=======
  @GET("{cloudProvider}/images/find")
  Call<List<Map>> findImage(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("cloudProvider") String cloudProvider,
      @Query("q") String query,
      @Query("account") String account,
      @Query("region") String region,
      @QueryMap Map additionalFilters);

<<<<<<< HEAD
  @GET("/tags")
  List<Map<String, Object>> getEntityTags(
=======
  @GET("tags")
  Call<List<Map<String, Object>>> getEntityTags(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Query("cloudProvider") String cloudProvider,
      @Query("entityType") String entityType,
      @Query("entityId") String entityId,
      @Query("account") String account,
      @Query("region") String region);

<<<<<<< HEAD
  @GET("/tags")
  List<Map> getEntityTags(@QueryMap Map parameters);

  @GET("/aws/cloudFormation/stacks/{stackId}")
  Map getCloudFormationStack(@Path(value = "stackId", encode = false) String stackId);

  @GET("/servicebroker/{account}/serviceInstance")
  Map<String, Object> getServiceInstance(
=======
  @GET("tags")
  Call<List<Map>> getEntityTags(@QueryMap Map parameters);

  @GET("aws/cloudFormation/stacks/{stackId}")
  Call<Map> getCloudFormationStack(@Path(value = "stackId", encoded = true) String stackId);

  @GET("servicebroker/{account}/serviceInstance")
  Call<Map<String, Object>> getServiceInstance(
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
      @Path("account") String account,
      @Query("cloudProvider") String cloudProvider,
      @Query("region") String region,
      @Query("serviceInstanceName") String serviceInstanceName);

<<<<<<< HEAD
  @GET("/credentials")
  List<Map<String, Object>> getCredentials(@Query("expand") boolean expand);
=======
  @GET("credentials")
  Call<List<Map<String, Object>>> getCredentials(@Query("expand") boolean expand);
>>>>>>> b2f2742ba0 (fix(retrofit2): remove leading slashes from all the retrofit2 api interfaces (#7159))
}
