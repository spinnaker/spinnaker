package com.netflix.spinnaker.clouddriver.tags;

import com.netflix.spinnaker.clouddriver.model.EntityTags;
import java.util.Collection;

/** Provides a mechanism for attaching arbitrary metadata to resources within cloud providers. */
public interface EntityTagger {

  String ENTITY_TYPE_SERVER_GROUP = "servergroup";
  String ENTITY_TYPE_CLUSTER = "cluster";

  void alert(
      String cloudProvider,
      String accountId,
      String region,
      String category,
      String entityType,
      String entityId,
      String key,
      String value,
      Long timestamp);

  void notice(
      String cloudProvider,
      String accountId,
      String region,
      String category,
      String entityType,
      String entityId,
      String key,
      String value,
      Long timestamp);

  void tag(
      String cloudProvider,
      String accountId,
      String region,
      String namespace,
      String entityType,
      String entityId,
      String tagName,
      Object value,
      Long timestamp);

  Collection<EntityTags> taggedEntities(
      String cloudProvider, String accountId, String entityType, String tagName, int maxResults);

  void deleteAll(
      String cloudProvider, String accountId, String region, String entityType, String entityId);

  void delete(
      String cloudProvider,
      String accountId,
      String region,
      String entityType,
      String entityId,
      String tagName);
}
