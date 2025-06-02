package com.netflix.spinnaker.orca.clouddriver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.clouddriver.model.*;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.utils.ServerGroupDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CloudDriverService {

  private static final TypeReference<List<ServerGroup>> SERVER_GROUPS = new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};
  private static final TypeReference<List<EntityTags>> ENTITY_TAGS = new TypeReference<>() {};
  private static final TypeReference<List<SearchResultSet>> SEARCH_RESULTS =
      new TypeReference<>() {};

  private final OortService oortService;

  private final ObjectMapper objectMapper;

  @Autowired
  public CloudDriverService(OortService oortService, ObjectMapper objectMapper) {
    this.oortService = oortService;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> getApplication(String application) {
    ResponseBody responseBody = Retrofit2SyncCall.execute(oortService.getApplication(application));
    return readBody(responseBody, JSON_MAP);
  }

  public List<ServerGroup> getServerGroups(String application) {
    return Retrofit2SyncCall.execute(oortService.getServerGroups(application));
  }

  public ServerGroup getServerGroup(String account, String region, String serverGroup) {
    ResponseBody responseBody =
        Retrofit2SyncCall.execute(oortService.getServerGroup(account, region, serverGroup));
    return readBody(responseBody, ServerGroup.class);
  }

  public List<SearchResultSet> getSearchResults(String searchTerm, String type, String platform) {
    ResponseBody responseBody =
        Retrofit2SyncCall.execute(oortService.getSearchResults(searchTerm, type, platform));
    return readBody(responseBody, SEARCH_RESULTS);
  }

  public Optional<TargetServerGroup> getTargetServerGroup(
      String account, String serverGroupName, String location) {
    return maybe(() -> getServerGroup(account, location, serverGroupName))
        .map(TargetServerGroup::new);
  }

  public ServerGroup getServerGroup(ServerGroupDescriptor descriptor) {
    ResponseBody responseBody =
        Retrofit2SyncCall.execute(
            oortService.getServerGroup(
                descriptor.getAccount(), descriptor.getRegion(), descriptor.getName()));
    return readBody(responseBody, ServerGroup.class);
  }

  public ServerGroup getServerGroupFromCluster(
      String app,
      String account,
      String cluster,
      String serverGroup,
      String region,
      String cloudProvider) {
    ResponseBody responseBody =
        Retrofit2SyncCall.execute(
            oortService.getServerGroupFromCluster(
                app, account, cluster, serverGroup, region, cloudProvider));
    return readBody(responseBody, ServerGroup.class);
  }

  public List<EntityTags> getEntityTags(
      String cloudProvider, String entityType, String entityId, String account, String region) {
    var response =
        Retrofit2SyncCall.execute(
            oortService.getEntityTags(cloudProvider, entityType, entityId, account, region));
    return objectMapper.convertValue(response, ENTITY_TAGS);
  }

  public List<EntityTags> getEntityTags(Map parameters) {
    List<Map> response = Retrofit2SyncCall.execute(oortService.getEntityTags(parameters));
    return objectMapper.convertValue(response, ENTITY_TAGS);
  }

  public List<Ami> getByAmiId(String type, String account, String region, Object imageId) {
    return Retrofit2SyncCall.execute(oortService.getByAmiId(type, account, region, imageId));
  }

  public Cluster getCluster(String app, String account, String cluster, String cloudProvider) {
    ResponseBody responseBody =
        Retrofit2SyncCall.execute(oortService.getCluster(app, account, cluster, cloudProvider));
    return readBody(responseBody, Cluster.class);
  }

  public Optional<Cluster> maybeCluster(
      String app, String account, String cluster, String cloudProvider) {
    return maybe(() -> getCluster(app, account, cluster, cloudProvider));
  }

  private static <T> Optional<T> maybe(Supplier<T> supplier) {
    try {
      T result = supplier.get();
      return Optional.ofNullable(result);
    } catch (SpinnakerHttpException spinnakerHttpException) {
      if (spinnakerHttpException.getResponseCode() == 404) {
        return Optional.empty();
      }
      throw spinnakerHttpException;
    }
  }

  @Deprecated
  /** @deprecated See {@link #getInstanceTyped(String, String, String)}.* */
  public Map<String, Object> getInstance(String account, String region, String instanceId) {
    ResponseBody responseBody =
        Retrofit2SyncCall.execute(oortService.getInstance(account, region, instanceId));
    return readBody(responseBody, JSON_MAP);
  }

  public Instance getInstanceTyped(String account, String region, String instanceId) {
    ResponseBody responseBody =
        Retrofit2SyncCall.execute(oortService.getInstance(account, region, instanceId));
    return readBody(responseBody, Instance.class);
  }

  @SneakyThrows // code may have depended on the exceptions thrown that groovy was hiding
  private <T> T readBody(ResponseBody responseBody, Class<T> type) {
    return objectMapper.readValue(responseBody.byteStream(), type);
  }

  @SneakyThrows // code may have depended on the exceptions thrown that groovy was hiding
  private <T> T readBody(ResponseBody responseBody, TypeReference<T> valueTypeRef) {
    return objectMapper.readValue(responseBody.byteStream(), valueTypeRef);
  }
}
