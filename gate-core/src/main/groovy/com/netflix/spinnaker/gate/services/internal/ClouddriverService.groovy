/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.services.internal

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import retrofit.client.Response
import retrofit.http.GET
import retrofit.http.Headers
import retrofit.http.Path
import retrofit.http.Query
import retrofit.http.QueryMap
import retrofit.http.Streaming

interface ClouddriverService {

  @GET('/credentials')
  List<Account> getAccounts()

  @GET('/credentials?expand=true')
  List<AccountDetails> getAccountDetails()

  @GET('/credentials/{account}')
  AccountDetails getAccount(@Path("account") String account)

  @GET('/task/{taskDetailsId}')
  Map getTaskDetails(@Path("taskDetailsId") String taskDetailsId)

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(Include.NON_NULL)
  static class Account {
    String name
    String accountId
    String type
    String providerVersion
    Collection<String> requiredGroupMembership = []
    String skin
    Map<String, Collection<String>> permissions
  }

  @JsonIgnoreProperties(ignoreUnknown = false)
  static class AccountDetails extends Account {
    String accountType
    String environment
    Boolean challengeDestructiveActions
    Boolean primaryAccount
    String cloudProvider
    private Map<String, Object> details = new HashMap<String, Object>()

    @JsonAnyGetter
    public Map<String,Object> details() {
      return details
    }

    @JsonAnySetter
    public void set(String name, Object value) {
      details.put(name, value)
    }
  }


  @Headers("Accept: application/json")
  @GET("/applications")
  List getApplications(@Query("expand") boolean expand)

  @Headers("Accept: application/json")
  @GET("/applications/{name}")
  Map getApplication(@Path("name") String name)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters")
  Map getClusters(@Path("name") String name)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}")
  List getClustersForAccount(@Path("name") String name,
                             @Path("account") String account)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}")
  List getCluster(@Path("name") String name,
                  @Path("account") String account,
                  @Path("cluster") String cluster)

  @Headers("Accept: application/json")
  @GET("/applications/{application}/clusters/{account}/{cluster}/{provider}/serverGroups/{serverGroupName}/scalingActivities")
  List getScalingActivities(@Path("application") String application,
                            @Path("account") String account,
                            @Path("cluster") String cluster,
                            @Path("provider") String provider,
                            @Path("serverGroupName") String serverGroupName,
                            @Query("region") String region)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}/{type}")
  Map getClusterByType(@Path("name") String name,
                       @Path("account") String account,
                       @Path("cluster") String cluster,
                       @Path("type") String type)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}/{type}/serverGroups/{serverGroupName}")
  List getServerGroup(@Path("name") String name,
                      @Path("account") String account,
                      @Path("cluster") String cluster,
                      @Path("type") String type,
                      @Path("serverGroupName") String serverGroupName)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}/{type}/{scope}/serverGroups/target/{target}")
  Map getTargetServerGroup(@Path("name") String application,
                           @Path("account") String account,
                           @Path("cluster") String cluster,
                           @Path("type") String type,
                           @Path("scope") String scope,
                           @Path("target") String target,
                           @Query("onlyEnabled") Boolean onlyEnabled,
                           @Query("validateOldest") Boolean validateOldest)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/serverGroups")
  List getServerGroups(@Path("name") String name,
                       @Query("expand") String expand,
                       @Query("cloudProvider") String cloudProvider,
                       @Query("clusters") String clusters)

  @Headers("Accept: application/json")
  @GET("/serverGroups")
  List getServerGroups(@Query("applications") List applications,
                       @Query("ids") List ids,
                       @Query("cloudProvider") String cloudProvider)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/jobs")
  List getJobs(@Path("name") String name, @Query("expand") String expand)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/jobs/{account}/{region}/{jobName}")
  Map getJobDetails(@Path("name") String name,
                    @Path("account") String account,
                    @Path("region") String region,
                    @Path("jobName") String jobName)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/serverGroups/{account}/{region}/{serverGroupName}")
  Map getServerGroupDetails(@Path("name") String appName,
                            @Path("account") String account,
                            @Path("region") String region,
                            @Path("serverGroupName") String serverGroupName)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}/{type}/loadBalancers")
  List getClusterLoadBalancers(@Path("name") String appName,
                               @Path("account") String account,
                               @Path("cluster") String cluster,
                               @Path("type") String type)

  @Headers("Accept: application/json")
  @GET("/{provider}/loadBalancers")
  List<Map> getLoadBalancers(@Path("provider") String provider)

  @Headers("Accept: application/json")
  @GET("/applications/{name}/loadBalancers")
  List<Map> getApplicationLoadBalancers(@Path("name") String appName)

  @Headers("Accept: application/json")
  @GET("/{provider}/loadBalancers/{name}")
  Map getLoadBalancer(@Path("provider") String provider,
                      @Path("name") String name)

  @Headers("Accept: application/json")
  @GET("/{provider}/loadBalancers/{account}/{region}/{name}")
  List<Map> getLoadBalancerDetails(@Path("provider") String provider,
                                   @Path("account") String account,
                                   @Path("region") String region,
                                   @Path("name") String name)

  @Headers("Accept: application/json")
  @GET("/instances/{account}/{region}/{instanceId}")
  Map getInstanceDetails(@Path("account") String account,
                         @Path("region") String region,
                         @Path("instanceId") String instanceId)

  @Headers("Accept: application/json")
  @GET("/instances/{account}/{region}/{instanceId}/console")
  Map getConsoleOutput(@Path("account") String account,
                       @Path("region") String region,
                       @Path("instanceId") String instanceId,
                       @Query("provider") String provider)

  @Headers("Accept: application/json")
  @GET("/{provider}/images/{account}/{region}/{imageId}")
  List<Map> getImageDetails(@Path("provider") String provider,
                            @Path("account") String account,
                            @Path("region") String region,
                            @Path("imageId") String imageId)

  @Headers("Accept: application/json")
  @GET("/projects/{project}/clusters")
  List<Map> getProjectClusters(@Path("project") String project)

  @Headers("Accept: application/json")
  @GET("/reports/reservation")
  List<Map> getReservationReports(@QueryMap Map<String, String> filters)

  @Headers("Accept: application/json")
  @GET("/reports/reservation/{name}")
  List<Map> getReservationReports(@Path("name") String name, @QueryMap Map<String, String> filters)

  @Headers("Accept: application/json")
  @GET("/{provider}/images/find")
  List<Map> findImages(@Path("provider") String provider,
                       @Query("q") String query,
                       @Query("region") String region,
                       @Query("account") String account,
                       @Query("count") Integer count,
                       @QueryMap Map additionalFilters)

  @Headers("Accept: application/json")
  @GET("/{provider}/images/tags")
  List<String> findTags(@Path("provider") String provider,
                       @Query("account") String account,
                       @Query("repository") String repository)

  @Headers("Accept: application/json")
  @GET("/search")
  List<Map> search(@Query("q") String query,
                   @Query("type") String type,
                   @Query("platform") String platform,
                   @Query("pageSize") Integer size,
                   @Query("page") Integer offset,
                   @QueryMap Map filters)

  @GET('/securityGroups')
  Map getSecurityGroups()

  @GET('/securityGroups/{account}/{type}')
  Map getSecurityGroups(@Path("account") String account, @Path("type") String type, @Query("region") String region)

  @GET('/securityGroups/{account}/{type}/{region}/{name}')
  Map getSecurityGroup(@Path("account") String account, @Path("type") String type, @Path("name") String name,
                       @Path("region") String region, @Query("vpcId") String vpcId)

  @GET("/applications/{application}/serverGroupManagers")
  List<Map> getServerGroupManagersForApplication(@Path("application") String application)

  @GET('/instanceTypes')
  List<Map> getInstanceTypes()

  @GET('/keyPairs')
  List<Map> getKeyPairs()

  @GET('/subnets')
  List<Map> getSubnets()

  @GET('/subnets/{cloudProvider}')
  List<Map> getSubnets(@Path("cloudProvider") String cloudProvider)

  @GET('/networks')
  Map getNetworks()

  @GET('/networks/{cloudProvider}')
  List<Map> getNetworks(@Path("cloudProvider") String cloudProvider)

  @GET('/cloudMetrics/{cloudProvider}/{account}/{region}')
  List<Map> findAllCloudMetrics(@Path("cloudProvider") String cloudProvider,
                    @Path("account") String account,
                    @Path("region") String region,
                    @QueryMap Map<String, String> filters)

  @GET('/cloudMetrics/{cloudProvider}/{account}/{region}/{metricName}/statistics')
  Map getCloudMetricStatistics(@Path("cloudProvider") String cloudProvider,
                               @Path("account") String account,
                               @Path("region") String region,
                               @Path("metricName") String metricName,
                               @Query("startTime") Long startTime,
                               @Query("endTime") Long endTime,
                               @QueryMap Map<String, String> filters)

  @GET('/tags')
  List<Map> listEntityTags(@QueryMap Map allParameters)

  @GET('/tags/{id}')
  Map getEntityTags(@Path('id') String id)

  @GET('/certificates')
  List<Map> getCertificates()

  @GET('/certificates/{cloudProvider}')
  List<Map> getCertificates(@Path("cloudProvider") String cloudProvider)

  @Streaming
  @GET('/v1/data/static/{id}')
  Response getStaticData(@Path('id') String id, @QueryMap Map<String, String> filters)

  @Streaming
  @GET('/v1/data/adhoc/{groupId}/{bucketId}/{objectId}')
  Response getAdhocData(@Path(value = 'groupId', encode = false) String groupId,
                        @Path(value = 'bucketId', encode = false) String bucketId,
                        @Path(value = 'objectId', encode = false) String objectId)

  @GET('/storage')
  List<String> getStorageAccounts()

  @GET('/artifacts/credentials')
  List<Map> getArtifactCredentials()

  @GET('/roles/{cloudProvider}')
  List<Map> getRoles(@Path("cloudProvider") String cloudProvider)

  @GET('/ecs/ecsClusters')
  List<Map> getAllEcsClusters()

  @GET('/ecs/cloudMetrics/alarms')
  List<Map> getEcsAllMetricAlarms()

  @GET('/manifests/{account}/{location}/{name}')
  Map getManifest(@Path(value = 'account') String account,
                  @Path(value = 'location') String location,
                  @Path(value = 'name') String name)
}
