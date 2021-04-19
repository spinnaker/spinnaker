package com.netflix.spinnaker.orca.clouddriver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.clouddriver.model.Cluster;
import com.netflix.spinnaker.orca.clouddriver.model.EntityTags;
import com.netflix.spinnaker.orca.clouddriver.model.Instance;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

@Component
public class CloudDriverService {

  private static final TypeReference<List<ServerGroup>> SERVER_GROUPS = new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};
  private static final TypeReference<List<EntityTags>> ENTITY_TAGS = new TypeReference<>() {};

  private final OortService oortService;

  private final ObjectMapper objectMapper;

  @Autowired
  public CloudDriverService(OortService oortService, ObjectMapper objectMapper) {
    this.oortService = oortService;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> getApplication(String application) {
    Response response = oortService.getApplication(application);
    return readBody(response, JSON_MAP);
  }

  public List<ServerGroup> getServerGroups(String application) {
    Response response = oortService.getServerGroups(application);
    return readBody(response, SERVER_GROUPS);
  }

  @Deprecated
  /** @deprecated See {@link #getServerGroupTyped(String, String, String)}.* */
  public Map<String, Object> getServerGroup(String account, String region, String serverGroup) {
    Response response = oortService.getServerGroup(account, region, serverGroup);
    return readBody(response, JSON_MAP);
  }

  public ServerGroup getServerGroupTyped(String account, String region, String serverGroup) {
    Response response = oortService.getServerGroup(account, region, serverGroup);
    return readBody(response, ServerGroup.class);
  }

  public Map<String, Object> getServerGroupFromCluster(
      String app,
      String account,
      String cluster,
      String serverGroup,
      String region,
      String cloudProvider) {
    Response response =
        oortService.getServerGroupFromCluster(
            app, account, cluster, serverGroup, region, cloudProvider);
    return readBody(response, JSON_MAP);
  }

  public List<Map<String, Object>> getEntityTags(
      String cloudProvider, String entityType, String entityId, String account, String region) {
    return oortService.getEntityTags(cloudProvider, entityType, entityId, account, region);
  }

  public List<EntityTags> getEntityTagsTyped(Map parameters) {
    List<Map> response = oortService.getEntityTags(parameters);
    return objectMapper.convertValue(response, ENTITY_TAGS);
  }

  public List<Map<String, Object>> getByAmiId(
      String type, String account, String region, Object imageId) {
    return oortService.getByAmiId(type, account, region, imageId);
  }

  @Deprecated
  /** @deprecated See {@link #getClusterTyped(String, String, String, String)}.* */
  public Map<String, Object> getCluster(
      String app, String account, String cluster, String cloudProvider) {
    Response response = oortService.getCluster(app, account, cluster, cloudProvider);
    return readBody(response, JSON_MAP);
  }

  public Cluster getClusterTyped(String app, String account, String cluster, String cloudProvider) {
    Response response = oortService.getCluster(app, account, cluster, cloudProvider);
    return readBody(response, Cluster.class);
  }

  @Deprecated
  /** @deprecated See {@link #getInstanceTyped(String, String, String)}.* */
  public Map<String, Object> getInstance(String account, String region, String instanceId) {
    Response response = oortService.getInstance(account, region, instanceId);
    return readBody(response, JSON_MAP);
  }

  public Instance getInstanceTyped(String account, String region, String instanceId) {
    Response response = oortService.getInstance(account, region, instanceId);
    return readBody(response, Instance.class);
  }

  @SneakyThrows // code may have depended on the exceptions thrown that groovy was hiding
  private <T> T readBody(Response response, Class<T> type) {
    return objectMapper.readValue(response.getBody().in(), type);
  }

  @SneakyThrows // code may have depended on the exceptions thrown that groovy was hiding
  private <T> T readBody(Response response, TypeReference<T> valueTypeRef) {
    return objectMapper.readValue(response.getBody().in(), valueTypeRef);
  }
}
