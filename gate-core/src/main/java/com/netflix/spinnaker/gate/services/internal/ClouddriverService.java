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
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.DELETE;
import retrofit.http.GET;
import retrofit.http.Headers;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;
import retrofit.http.Query;
import retrofit.http.QueryMap;
import retrofit.http.Streaming;

public interface ClouddriverService {
  @GET("/credentials")
  List<Account> getAccounts();

  @GET("/credentials?expand=true")
  List<AccountDetails> getAccountDetails();

  @GET("/credentials/{account}")
  AccountDetails getAccount(@Path("account") String account);

  @GET("/credentials/type/{type}")
  List<AccountDefinition> getAccountDefinitionsByType(
      @Path("type") String type,
      @Query("limit") Integer limit,
      @Query("startingAccountName") String startingAccountName);

  @POST("/credentials")
  AccountDefinition createAccountDefinition(@Body AccountDefinition accountDefinition);

  @PUT("/credentials")
  AccountDefinition updateAccountDefinition(@Body AccountDefinition accountDefinition);

  @DELETE("/credentials/{account}")
  Response deleteAccountDefinition(@Path("account") String account);

  @GET("/task/{taskDetailsId}")
  Map getTaskDetails(@Path("taskDetailsId") String taskDetailsId);

  @Headers("Accept: application/json")
  @GET("/applications")
  List getApplications(@Query("expand") boolean expand);

  @Headers("Accept: application/json")
  @GET("/applications?restricted=false")
  List getAllApplicationsUnrestricted(@Query("expand") boolean expand);

  @Headers("Accept: application/json")
  @GET("/applications/{name}")
  Map getApplication(@Path("name") String name);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters")
  Map getClusters(@Path("name") String name);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}")
  List getClustersForAccount(@Path("name") String name, @Path("account") String account);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}")
  List getCluster(
      @Path("name") String name, @Path("account") String account, @Path("cluster") String cluster);

  @Headers("Accept: application/json")
  @GET(
      "/applications/{application}/clusters/{account}/{cluster}/{provider}/serverGroups/{serverGroupName}/scalingActivities")
  List getScalingActivities(
      @Path("application") String application,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("provider") String provider,
      @Path("serverGroupName") String serverGroupName,
      @Query("region") String region);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}/{type}")
  Map getClusterByType(
      @Path("name") String name,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("type") String type);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}/{type}/serverGroups/{serverGroupName}")
  List getServerGroup(
      @Path("name") String name,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("type") String type,
      @Path("serverGroupName") String serverGroupName);

  @Headers("Accept: application/json")
  @GET(
      "/applications/{name}/clusters/{account}/{cluster}/{type}/{scope}/serverGroups/target/{target}")
  Map getTargetServerGroup(
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
  List<Map<String, Object>> getApplicationRawResources(@Path("name") String appName);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/serverGroups")
  List getServerGroups(
      @Path("name") String name,
      @Query("expand") String expand,
      @Query("cloudProvider") String cloudProvider,
      @Query("clusters") String clusters);

  @Headers("Accept: application/json")
  @GET("/serverGroups")
  List getServerGroups(
      @Query("applications") List applications,
      @Query("ids") List ids,
      @Query("cloudProvider") String cloudProvider);

  @Headers("Accept: application/json")
  @POST("/applications/{name}/jobs/{account}/{region}/{jobName}")
  Map getJobDetails(
      @Path("name") String name,
      @Path("account") String account,
      @Path("region") String region,
      @Path("jobName") String jobName,
      @Body String emptyStringForRetrofit);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/serverGroups/{account}/{region}/{serverGroupName}")
  Map getServerGroupDetails(
      @Path("name") String appName,
      @Path("account") String account,
      @Path("region") String region,
      @Path("serverGroupName") String serverGroupName,
      @Query("includeDetails") String includeDetails);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/clusters/{account}/{cluster}/{type}/loadBalancers")
  List getClusterLoadBalancers(
      @Path("name") String appName,
      @Path("account") String account,
      @Path("cluster") String cluster,
      @Path("type") String type);

  @Headers("Accept: application/json")
  @GET("/{provider}/loadBalancers")
  List<Map> getLoadBalancers(@Path("provider") String provider);

  @Headers("Accept: application/json")
  @GET("/applications/{name}/loadBalancers")
  List<Map> getApplicationLoadBalancers(@Path("name") String appName);

  @Headers("Accept: application/json")
  @GET("/{provider}/loadBalancers/{name}")
  Map getLoadBalancer(@Path("provider") String provider, @Path("name") String name);

  @Headers("Accept: application/json")
  @GET("/{provider}/loadBalancers/{account}/{region}/{name}")
  List<Map> getLoadBalancerDetails(
      @Path("provider") String provider,
      @Path("account") String account,
      @Path("region") String region,
      @Path("name") String name);

  @Headers("Accept: application/json")
  @GET("/instances/{account}/{region}/{instanceId}")
  Map getInstanceDetails(
      @Path("account") String account,
      @Path("region") String region,
      @Path("instanceId") String instanceId);

  @Headers("Accept: application/json")
  @GET("/instances/{account}/{region}/{instanceId}/console")
  Map getConsoleOutput(
      @Path("account") String account,
      @Path("region") String region,
      @Path("instanceId") String instanceId,
      @Query("provider") String provider);

  @Headers("Accept: application/json")
  @GET("/{provider}/images/{account}/{region}/{imageId}")
  List<Map> getImageDetails(
      @Path("provider") String provider,
      @Path("account") String account,
      @Path("region") String region,
      @Path("imageId") String imageId);

  @Headers("Accept: application/json")
  @GET("/projects/{project}/clusters")
  List<Map> getProjectClusters(@Path("project") String project);

  @Headers("Accept: application/json")
  @GET("/reports/reservation")
  List<Map> getReservationReports(@QueryMap Map<String, String> filters);

  @Headers("Accept: application/json")
  @GET("/reports/reservation/{name}")
  List<Map> getReservationReports(@Path("name") String name, @QueryMap Map<String, String> filters);

  @Headers("Accept: application/json")
  @GET("/{provider}/images/find")
  List<Map> findImages(
      @Path("provider") String provider,
      @Query("q") String query,
      @Query("region") String region,
      @Query("account") String account,
      @Query("count") Integer count,
      @QueryMap Map additionalFilters);

  @Headers("Accept: application/json")
  @GET("/{provider}/images/tags")
  List<String> findTags(
      @Path("provider") String provider,
      @Query("account") String account,
      @Query("repository") String repository);

  @Headers("Accept: application/json")
  @GET("/search")
  List<Map> search(
      @Query("q") String query,
      @Query("type") String type,
      @Query("platform") String platform,
      @Query("pageSize") Integer size,
      @Query("page") Integer offset,
      @QueryMap Map filters);

  @GET("/securityGroups")
  Map getSecurityGroups();

  @GET("/securityGroups/{account}/{type}")
  Map getSecurityGroups(@Path("account") String account, @Path("type") String type);

  @GET("/securityGroups/{account}/{type}")
  List getSecurityGroupsForRegion(
      @Path("account") String account, @Path("type") String type, @Query("region") String region);

  @GET("/securityGroups/{account}/{type}/{region}/{name}")
  Map getSecurityGroup(
      @Path("account") String account,
      @Path("type") String type,
      @Path("name") String name,
      @Path("region") String region,
      @Query("vpcId") String vpcId);

  @GET("/applications/{application}/serverGroupManagers")
  List<Map> getServerGroupManagersForApplication(@Path("application") String application);

  @GET("/instanceTypes")
  List<Map> getInstanceTypes();

  @GET("/keyPairs")
  List<Map> getKeyPairs();

  @GET("/subnets")
  List<Map> getSubnets();

  @GET("/subnets/{cloudProvider}")
  List<Map> getSubnets(@Path("cloudProvider") String cloudProvider);

  @GET("/networks")
  Map getNetworks();

  @GET("/networks/{cloudProvider}")
  List<Map> getNetworks(@Path("cloudProvider") String cloudProvider);

  @GET("/cloudMetrics/{cloudProvider}/{account}/{region}")
  List<Map> findAllCloudMetrics(
      @Path("cloudProvider") String cloudProvider,
      @Path("account") String account,
      @Path("region") String region,
      @QueryMap Map<String, String> filters);

  @GET("/cloudMetrics/{cloudProvider}/{account}/{region}/{metricName}/statistics")
  Map getCloudMetricStatistics(
      @Path("cloudProvider") String cloudProvider,
      @Path("account") String account,
      @Path("region") String region,
      @Path("metricName") String metricName,
      @Query("startTime") Long startTime,
      @Query("endTime") Long endTime,
      @QueryMap Map<String, String> filters);

  @GET("/tags")
  List<Map> listEntityTags(@QueryMap Map allParameters);

  @GET("/tags/{id}")
  Map getEntityTags(@Path("id") String id);

  @GET("/certificates")
  List<Map> getCertificates();

  @GET("/certificates/{cloudProvider}")
  List<Map> getCertificates(@Path("cloudProvider") String cloudProvider);

  @Streaming
  @GET("/v1/data/static/{id}")
  Response getStaticData(@Path("id") String id, @QueryMap Map<String, String> filters);

  @Streaming
  @GET("/v1/data/adhoc/{groupId}/{bucketId}/{objectId}")
  Response getAdhocData(
      @Path(value = "groupId", encode = false) String groupId,
      @Path(value = "bucketId", encode = false) String bucketId,
      @Path(value = "objectId", encode = false) String objectId);

  @GET("/storage")
  List<String> getStorageAccounts();

  @GET("/artifacts/credentials")
  List<Map> getArtifactCredentials();

  @Streaming
  @PUT("/artifacts/fetch")
  Response getArtifactContent(@Body Map artifact);

  @GET("/artifacts/account/{accountName}/names")
  List<String> getArtifactNames(
      @Path("accountName") String accountName, @Query("type") String type);

  @GET("/artifacts/account/{accountName}/versions")
  List<String> getArtifactVersions(
      @Path("accountName") String accountName,
      @Query("type") String type,
      @Query("artifactName") String artifactName);

  @GET("/roles/{cloudProvider}")
  List<Map> getRoles(@Path("cloudProvider") String cloudProvider);

  @GET("/ecs/ecsClusters")
  List<Map> getAllEcsClusters();

  @GET("/ecs/cloudMetrics/alarms")
  List<Map> getEcsAllMetricAlarms();

  @GET("/ecs/secrets")
  List<Map> getAllEcsSecrets();

  @GET("/ecs/ecsClusterDescriptions/{account}/{region}")
  List<Map> getEcsClusterDescriptions(
      @Path(value = "account") String account, @Path(value = "region") String region);

  @GET("/ecs/serviceDiscoveryRegistries")
  List<Map> getAllEcsServiceDiscoveryRegistries();

  @GET("/manifests/{account}/{location}/{name}")
  Map getManifest(
      @Path(value = "account") String account,
      @Path(value = "location") String location,
      @Path(value = "name") String name);

  @GET("/applications/{application}/serverGroups/{account}/{serverGroupName}/events")
  List<Map> getServerGroupEvents(
      @Path(value = "application") String application,
      @Path(value = "account") String account,
      @Path(value = "serverGroupName") String serverGroupName,
      @Query("region") String region,
      @Query("provider") String provider);

  @GET("/servicebroker/{account}/services")
  List<Map> listServices(
      @Query(value = "cloudProvider") String cloudProvider,
      @Query(value = "region") String region,
      @Path(value = "account") String account);

  @GET("/servicebroker/{account}/serviceInstance")
  Map getServiceInstance(
      @Path(value = "account") String account,
      @Query(value = "cloudProvider") String cloudProvider,
      @Query(value = "region") String region,
      @Query(value = "serviceInstanceName") String serviceInstanceName);

  @GET(value = "/functions")
  List<Map> getFunctions(
      @Query(value = "functionName") String functionName,
      @Query(value = "region") String region,
      @Query(value = "account") String account);

  @GET("/applications/{name}/functions")
  List<Map> getApplicationFunctions(@Path("name") String appName);

  @GET("/installedPlugins")
  List<SpinnakerPluginDescriptor> getInstalledPlugins();

  @GET("/artifacts/content-address/{application}/{hash}")
  Artifact.StoredView getStoredArtifact(
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
