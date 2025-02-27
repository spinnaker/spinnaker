package com.netflix.spinnaker.gate.services.internal;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;
import retrofit2.http.Streaming;

public interface ClouddriverService {
  @GET("/credentials")
  Call<List<Account>> getAccounts();

  @GET("/credentials?expand=true")
  Call<List<AccountDetails>> getAccountDetails();

  @GET("/credentials/{account}")
  Call<AccountDetails> getAccount(@Path("account") String account);

  @GET("/credentials/type/{type}")
  Call<List<AccountDefinition>> getAccountDefinitionsByType(
      @Path("type") String type,
      @Query("limit") Integer limit,
      @Query("startingAccountName") String startingAccountName);

  @POST("/credentials")
  Call<AccountDefinition> createAccountDefinition(@Body AccountDefinition accountDefinition);

  @PUT("/credentials")
  Call<AccountDefinition> updateAccountDefinition(@Body AccountDefinition accountDefinition);

  @DELETE("/credentials/{account}")
  Call<ResponseBody> deleteAccountDefinition(@Path("account") String account);

  @GET("/task/{taskDetailsId}")
  Call<Map> getTaskDetails(@Path("taskDetailsId") String taskDetailsId);

  @Headers("Accept: application/json")
  @GET("/applications")
  Call<List> getApplications(@Query("expand") boolean expand);

  @Headers("Accept: application/json")
  @GET("/applications?restricted=false")
  Call<List> getAllApplicationsUnrestricted(@Query("expand") boolean expand);

  @Headers("Accept: application/json")
  @GET("/applications/{name}")
  Call<Map> getApplication(@Path("name") String name);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters")
  Call<Map> getClusters(@Path("name") String name);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}")
  Call<List> getClustersForAccount(@Path("name") String name, @Path("account") String account);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}")
  Call<List> getCluster(
      @Path("name") String name, @Path("account") String account, @Path("cluster") String cluster);

  @Headers("Accept: application/json")
  @GET(
      "/applications/{application}/clusters/{account}/{cluster}/{provider}/serverGroups/{serverGroupName}/scalingActivities")
  Call<List> getScalingActivities(
      @Path("application") String application,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("provider") String provider,
      @Path("serverGroupName") String serverGroupName,
      @Query("region") String region);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}/{type}")
  Call<Map> getClusterByType(
      @Path("name") String name,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("type") String type);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}/{type}/serverGroups/{serverGroupName}")
  Call<List> getServerGroup(
      @Path("name") String name,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("type") String type,
      @Path("serverGroupName") String serverGroupName);

  @Headers("Accept: application/json")
  @GET(
      "/applications/{name}/clusters/{account}/{cluster}/{type}/{scope}/serverGroups/target/{target}")
  Call<Map> getTargetServerGroup(
      @Path("name") String application,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("type") String type,
      @Path("scope") String scope,
      @Path("target") String target,
      @Query("onlyEnabled") Boolean onlyEnabled,
      @Query("validateOldest") Boolean validateOldest);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/rawResources")
  Call<List<Map<String, Object>>> getApplicationRawResources(@Path("name") String appName);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/serverGroups")
  Call<List> getServerGroups(
      @Path("name") String name,
      @Query("expand") String expand,
      @Query("cloudProvider") String cloudProvider,
      @Query("clusters") String clusters);

  @Headers("Accept: application/json")
  @GET("/serverGroups")
  Call<List> getServerGroups(
      @Query("applications") List applications,
      @Query("ids") List ids,
      @Query("cloudProvider") String cloudProvider);

  @Headers("Accept: application/json")
  @POST("/applications/{name}/jobs/{account}/{region}/{jobName}")
  Call<Map> getJobDetails(
      @Path("name") String name,
      @Path("account") String account,
      @Path("region") String region,
      @Path("jobName") String jobName,
      @Body String emptyStringForRetrofit);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/serverGroups/{account}/{region}/{serverGroupName}")
  Call<Map> getServerGroupDetails(
      @Path("name") String appName,
      @Path("account") String account,
      @Path("region") String region,
      @Path("serverGroupName") String serverGroupName,
      @Query("includeDetails") String includeDetails);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}/{type}/loadBalancers")
  Call<List> getClusterLoadBalancers(
      @Path("name") String appName,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("type") String type);

  @Headers("Accept: application/json")
  @GET("/{provider}/loadBalancers")
  Call<List<Map>> getLoadBalancers(@Path("provider") String provider);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/loadBalancers")
  Call<List<Map>> getApplicationLoadBalancers(@Path("name") String appName);

  @Headers("Accept: application/json")
  @GET("/{provider}/loadBalancers/{name}")
  Call<Map> getLoadBalancer(@Path("provider") String provider, @Path("name") String name);

  @Headers("Accept: application/json")
  @GET("/{provider}/loadBalancers/{account}/{region}/{name}")
  Call<List<Map>> getLoadBalancerDetails(
      @Path("provider") String provider,
      @Path("account") String account,
      @Path("region") String region,
      @Path("name") String name);

  @Headers("Accept: application/json")
  @GET("/instances/{account}/{region}/{instanceId}")
  Call<Map> getInstanceDetails(
      @Path("account") String account,
      @Path("region") String region,
      @Path("instanceId") String instanceId);

  @Headers("Accept: application/json")
  @GET("/instances/{account}/{region}/{instanceId}/console")
  Call<Map> getConsoleOutput(
      @Path("account") String account,
      @Path("region") String region,
      @Path("instanceId") String instanceId,
      @Query("provider") String provider);

  @Headers("Accept: application/json")
  @GET("/{provider}/images/{account}/{region}/{imageId}")
  Call<List<Map>> getImageDetails(
      @Path("provider") String provider,
      @Path("account") String account,
      @Path("region") String region,
      @Path("imageId") String imageId);

  @Headers("Accept: application/json")
  @GET("/projects/{project}/clusters")
  Call<List<Map>> getProjectClusters(@Path("project") String project);

  @Headers("Accept: application/json")
  @GET("/reports/reservation")
  Call<List<Map>> getReservationReports(@QueryMap Map<String, String> filters);

  @Headers("Accept: application/json")
  @GET("/reports/reservation/{name}")
  Call<List<Map>> getReservationReports(
      @Path("name") String name, @QueryMap Map<String, String> filters);

  @Headers("Accept: application/json")
  @GET("/{provider}/images/find")
  Call<List<Map>> findImages(
      @Path("provider") String provider,
      @Query("q") String query,
      @Query("region") String region,
      @Query("account") String account,
      @Query("count") Integer count,
      @QueryMap Map<String, String> additionalFilters);

  @Headers("Accept: application/json")
  @GET("/{provider}/images/tags")
  Call<List<String>> findTags(
      @Path("provider") String provider,
      @Query("account") String account,
      @Query("repository") String repository);

  @Headers("Accept: application/json")
  @GET("/search")
  Call<List<Map>> search(
      @Query("q") String query,
      @Query("type") String type,
      @Query("platform") String platform,
      @Query("pageSize") Integer size,
      @Query("page") Integer offset,
      @QueryMap Map<String, String> filters);

  @GET("/securityGroups")
  Call<Map> getSecurityGroups();

  @GET("/securityGroups/{account}/{type}")
  Call<Map> getSecurityGroups(@Path("account") String account, @Path("type") String type);

  @GET("/securityGroups/{account}/{type}")
  Call<List> getSecurityGroupsForRegion(
      @Path("account") String account, @Path("type") String type, @Query("region") String region);

  @GET("/securityGroups/{account}/{type}/{region}/{name}")
  Call<Map> getSecurityGroup(
      @Path("account") String account,
      @Path("type") String type,
      @Path("name") String name,
      @Path("region") String region,
      @Query("vpcId") String vpcId);

  @GET("/applications/{application}/serverGroupManagers")
  Call<List<Map>> getServerGroupManagersForApplication(@Path("application") String application);

  @GET("/instanceTypes")
  Call<List<Map>> getInstanceTypes();

  @GET("/keyPairs")
  Call<List<Map>> getKeyPairs();

  @GET("/subnets")
  Call<List<Map>> getSubnets();

  @GET("/subnets/{cloudProvider}")
  Call<List<Map>> getSubnets(@Path("cloudProvider") String cloudProvider);

  @GET("/networks")
  Call<Map> getNetworks();

  @GET("/networks/{cloudProvider}")
  Call<List<Map>> getNetworks(@Path("cloudProvider") String cloudProvider);

  @GET("/cloudMetrics/{cloudProvider}/{account}/{region}")
  Call<List<Map>> findAllCloudMetrics(
      @Path("cloudProvider") String cloudProvider,
      @Path("account") String account,
      @Path("region") String region,
      @QueryMap Map<String, String> filters);

  @GET("/cloudMetrics/{cloudProvider}/{account}/{region}/{metricName}/statistics")
  Call<Map> getCloudMetricStatistics(
      @Path("cloudProvider") String cloudProvider,
      @Path("account") String account,
      @Path("region") String region,
      @Path("metricName") String metricName,
      @Query("startTime") Long startTime,
      @Query("endTime") Long endTime,
      @QueryMap Map<String, String> filters);

  @GET("/tags")
  Call<List<Map>> listEntityTags(@QueryMap Map<String, Object> allParameters);

  @GET("/tags/{id}")
  Call<Map> getEntityTags(@Path("id") String id);

  @GET("/certificates")
  Call<List<Map>> getCertificates();

  @GET("/certificates/{cloudProvider}")
  Call<List<Map>> getCertificates(@Path("cloudProvider") String cloudProvider);

  @Streaming
  @GET("/v1/data/static/{id}")
  Call<ResponseBody> getStaticData(@Path("id") String id, @QueryMap Map<String, String> filters);

  @Streaming
  @GET("/v1/data/adhoc/{groupId}/{bucketId}/{objectId}")
  Call<ResponseBody> getAdhocData(
      @Path(value = "groupId", encoded = true) String groupId,
      @Path(value = "bucketId", encoded = true) String bucketId,
      @Path(value = "objectId", encoded = true) String objectId);

  @GET("/storage")
  Call<List<String>> getStorageAccounts();

  @GET("/artifacts/credentials")
  Call<List<Map>> getArtifactCredentials();

  @Streaming
  @PUT("/artifacts/fetch")
  Call<ResponseBody> getArtifactContent(@Body Map artifact);

  @GET("/artifacts/account/{accountName}/names")
  Call<List<String>> getArtifactNames(
      @Path("accountName") String accountName, @Query("type") String type);

  @GET("/artifacts/account/{accountName}/versions")
  Call<List<String>> getArtifactVersions(
      @Path("accountName") String accountName,
      @Query("type") String type,
      @Query("artifactName") String artifactName);

  @GET("/roles/{cloudProvider}")
  Call<List<Map>> getRoles(@Path("cloudProvider") String cloudProvider);

  @GET("/ecs/ecsClusters")
  Call<List<Map>> getAllEcsClusters();

  @GET("/ecs/cloudMetrics/alarms")
  Call<List<Map>> getEcsAllMetricAlarms();

  @GET("/ecs/secrets")
  Call<List<Map>> getAllEcsSecrets();

  @GET("/ecs/ecsClusterDescriptions/{account}/{region}")
  Call<List<Map>> getEcsClusterDescriptions(
      @Path(value = "account") String account, @Path(value = "region") String region);

  @GET("/ecs/serviceDiscoveryRegistries")
  Call<List<Map>> getAllEcsServiceDiscoveryRegistries();

  @GET("/manifests/{account}/{location}/{name}")
  Call<Map> getManifest(
      @Path(value = "account") String account,
      @Path(value = "location") String location,
      @Path(value = "name") String name);

  @GET("/applications/{application}/serverGroups/{account}/{serverGroupName}/events")
  Call<List<Map>> getServerGroupEvents(
      @Path(value = "application") String application,
      @Path(value = "account") String account,
      @Path(value = "serverGroupName") String serverGroupName,
      @Query("region") String region,
      @Query("provider") String provider);

  @GET("/servicebroker/{account}/services")
  Call<List<Map>> listServices(
      @Path(value = "account") String account,
      @Query(value = "cloudProvider") String cloudProvider,
      @Query(value = "region") String region);

  @GET("/servicebroker/{account}/serviceInstance")
  Call<Map> getServiceInstance(
      @Path(value = "account") String account,
      @Query(value = "cloudProvider") String cloudProvider,
      @Query(value = "region") String region,
      @Query(value = "serviceInstanceName") String serviceInstanceName);

  @GET(value = "/functions")
  Call<List<Map>> getFunctions(
      @Query(value = "functionName") String functionName,
      @Query(value = "region") String region,
      @Query(value = "account") String account);

  @GET("/applications/{name}/functions")
  Call<List<Map>> getApplicationFunctions(@Path("name") String appName);

  @GET("/installedPlugins")
  Call<List<SpinnakerPluginDescriptor>> getInstalledPlugins();

  @GET("/artifacts/content-address/{application}/{hash}")
  Call<Artifact.StoredView> getStoredArtifact(
      @Path("application") String application, @Path("hash") String hash);

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  class Account {
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getAccountId() {
      return accountId;
    }

    public void setAccountId(String accountId) {
      this.accountId = accountId;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Collection<String> getRequiredGroupMembership() {
      return requiredGroupMembership;
    }

    public void setRequiredGroupMembership(Collection<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership;
    }

    public Map<String, Collection<String>> getPermissions() {
      return permissions;
    }

    public void setPermissions(Map<String, Collection<String>> permissions) {
      this.permissions = permissions;
    }

    private String name;
    private String accountId;
    private String type;
    private Collection<String> requiredGroupMembership = new ArrayList<String>();
    private Map<String, Collection<String>> permissions;
  }

  @JsonIgnoreProperties(ignoreUnknown = false)
  class AccountDetails extends Account {
    @JsonAnyGetter
    public Map<String, Object> details() {
      return details;
    }

    @JsonAnySetter
    public void set(String name, Object value) {
      details.put(name, value);
    }

    public String getAccountType() {
      return accountType;
    }

    public void setAccountType(String accountType) {
      this.accountType = accountType;
    }

    public String getEnvironment() {
      return environment;
    }

    public void setEnvironment(String environment) {
      this.environment = environment;
    }

    public Boolean getChallengeDestructiveActions() {
      return challengeDestructiveActions;
    }

    public void setChallengeDestructiveActions(Boolean challengeDestructiveActions) {
      this.challengeDestructiveActions = challengeDestructiveActions;
    }

    public Boolean getPrimaryAccount() {
      return primaryAccount;
    }

    public void setPrimaryAccount(Boolean primaryAccount) {
      this.primaryAccount = primaryAccount;
    }

    public String getCloudProvider() {
      return cloudProvider;
    }

    public void setCloudProvider(String cloudProvider) {
      this.cloudProvider = cloudProvider;
    }

    private String accountType;
    private String environment;
    private Boolean challengeDestructiveActions;
    private Boolean primaryAccount;
    private String cloudProvider;
    private final Map<String, Object> details = new HashMap<String, Object>();
  }

  /**
   * Wrapper type for Clouddriver account definitions. Clouddriver account definitions implement
   * {@code CredentialsDefinition}, and its type discriminator is present in a property named
   * {@code @type}. An instance of an account definition may have fairly different properties than
   * its corresponding {@code AccountCredentials} instance. Account definitions must store all the
   * relevant properties unchanged while {@link Account} and {@link AccountDetails} may summarize
   * and remove data returned from their corresponding APIs. Account definitions must be transformed
   * by a {@code CredentialsParser} before their corresponding credentials may be used by
   * Clouddriver.
   */
  class AccountDefinition {
    private final Map<String, Object> details = new HashMap<>();
    private String type;
    private String name;

    @JsonAnyGetter
    public Map<String, Object> details() {
      return details;
    }

    @JsonAnySetter
    public void set(String name, Object value) {
      details.put(name, value);
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
