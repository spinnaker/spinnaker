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

package com.netflix.spinnaker.clouddriver.elasticsearch;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.DeleteEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.UpsertEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import com.netflix.spinnaker.clouddriver.elasticsearch.ops.DeleteEntityTagsAtomicOperation;
import com.netflix.spinnaker.clouddriver.elasticsearch.ops.UpsertEntityTagsAtomicOperation;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.tags.EntityTagger;
import com.netflix.spinnaker.kork.core.RetrySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ElasticSearchEntityTagger implements EntityTagger {
  private static final Logger log = LoggerFactory.getLogger(ElasticSearchEntityTagger.class);

  static final String ALERT_TYPE = "alert";
  static final String ALERT_KEY_PREFIX = "spinnaker_ui_alert:";

  private static final String NOTICE_TYPE = "notice";
  private static final String NOTICE_KEY_PREFIX = "spinnaker_ui_notice:";

  private final RetrySupport retrySupport;
  private final Front50Service front50Service;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final ElasticSearchEntityTagsProvider entityTagsProvider;

  public ElasticSearchEntityTagger(RetrySupport retrySupport,
                                   Front50Service front50Service,
                                   AccountCredentialsProvider accountCredentialsProvider,
                                   ElasticSearchEntityTagsProvider entityTagsProvider) {
    this.retrySupport = retrySupport;
    this.front50Service = front50Service;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.entityTagsProvider = entityTagsProvider;
  }

  @Override
  public void alert(String cloudProvider,
                    String accountId,
                    String region,
                    String category,
                    String entityType,
                    String entityId,
                    String key,
                    String value,
                    Long timestamp) {
    upsertEntityTags(
      ALERT_TYPE, ALERT_KEY_PREFIX,
      cloudProvider,
      accountId,
      region,
      category,
      entityType,
      entityId,
      key,
      value,
      timestamp
    );
  }

  @Override
  public void notice(String cloudProvider,
                     String accountId,
                     String region,
                     String category,
                     String entityType,
                     String entityId,
                     String key,
                     String value,
                     Long timestamp) {
    upsertEntityTags(
      NOTICE_TYPE,
      NOTICE_KEY_PREFIX,
      cloudProvider,
      accountId,
      region,
      category,
      entityType,
      entityId,
      key,
      value,
      timestamp
    );
  }

  @Override
  public Collection<EntityTags> taggedEntities(String cloudProvider,
                                               String accountId,
                                               String entityType,
                                               String tagName,
                                               int maxResults) {
    return entityTagsProvider.getAll(
      cloudProvider,
      null,
      entityType,
      null,
      null,
      accountId,
      null,
      null,
      Collections.singletonMap(tagName, "*"),
      maxResults
    );
  }

  @Override
  public void deleteAll(String cloudProvider, String accountId, String region, String entityType, String entityId) {
    DeleteEntityTagsDescription deleteEntityTagsDescription = deleteEntityTagsDescription(
      cloudProvider,
      accountId,
      region,
      entityType,
      entityId,
      null
    );
    log.info("Removing all entity tags for '{}'", deleteEntityTagsDescription.getId());

    delete(deleteEntityTagsDescription);
  }

  @Override
  public void delete(String cloudProvider,
                     String accountId,
                     String region,
                     String entityType,
                     String entityId,
                     String tagName) {
    DeleteEntityTagsDescription deleteEntityTagsDescription = deleteEntityTagsDescription(
      cloudProvider,
      accountId,
      region,
      entityType,
      entityId,
      Collections.singletonList(tagName)
    );
    log.info("Removing '{}' for '{}'", tagName, deleteEntityTagsDescription.getId());

    delete(deleteEntityTagsDescription);
  }

  @VisibleForTesting
  protected void run(DeleteEntityTagsAtomicOperation deleteEntityTagsAtomicOperation) {
    deleteEntityTagsAtomicOperation.operate(Collections.emptyList());
  }

  private void delete(DeleteEntityTagsDescription deleteEntityTagsDescription) {
    DeleteEntityTagsAtomicOperation deleteEntityTagsAtomicOperation = new DeleteEntityTagsAtomicOperation(
      front50Service,
      entityTagsProvider,
      deleteEntityTagsDescription
    );

    Task originalTask = TaskRepository.threadLocalTask.get();
    try {
      TaskRepository.threadLocalTask.set(
        Optional.ofNullable(originalTask).orElse(new DefaultTask(ElasticSearchEntityTagger.class.getSimpleName()))
      );
      run(deleteEntityTagsAtomicOperation);
    } finally {
      TaskRepository.threadLocalTask.set(originalTask);
    }
  }

  private void upsertEntityTags(String type,
                                String prefix,
                                String cloudProvider,
                                String accountId,
                                String region,
                                String category,
                                String entityType,
                                String entityId,
                                String key,
                                String value,
                                Long timestamp) {
    UpsertEntityTagsAtomicOperation upsertEntityTagsAtomicOperation = new UpsertEntityTagsAtomicOperation(
      retrySupport,
      front50Service,
      accountCredentialsProvider,
      entityTagsProvider,
      upsertEntityTagsDescription(
        type, prefix, cloudProvider, accountId, region, category, entityType, entityId, key, value, timestamp
      )
    );

    try {
      TaskRepository.threadLocalTask.set(new DefaultTask(this.getClass().getSimpleName()));
      upsertEntityTagsAtomicOperation.operate(Collections.emptyList());
    } finally {
      TaskRepository.threadLocalTask.set(null);
    }
  }

  private static UpsertEntityTagsDescription upsertEntityTagsDescription(String type,
                                                                         String prefix,
                                                                         String cloudProvider,
                                                                         String accountId,
                                                                         String region,
                                                                         String category,
                                                                         String entityType,
                                                                         String entityId,
                                                                         String key,
                                                                         String value,
                                                                         Long timestamp) {
    EntityTags.EntityRef entityRef = new EntityTags.EntityRef();
    entityRef.setEntityType(entityType);
    entityRef.setEntityId(entityId);
    entityRef.setCloudProvider(cloudProvider);
    entityRef.setAccountId(accountId);
    entityRef.setRegion(region);

    Map<String, String> entityTagValue = new HashMap<>();
    entityTagValue.put("message", value);
    entityTagValue.put("type", type);

    EntityTags.EntityTag entityTag = new EntityTags.EntityTag();
    entityTag.setName(prefix + key);
    entityTag.setValue(entityTagValue);
    entityTag.setCategory(category);
    entityTag.setTimestamp(timestamp);
    entityTag.setValueType(EntityTags.EntityTagValueType.object);

    UpsertEntityTagsDescription upsertEntityTagsDescription = new UpsertEntityTagsDescription();
    upsertEntityTagsDescription.setEntityRef(entityRef);
    upsertEntityTagsDescription.setTags(Collections.singletonList(entityTag));

    return upsertEntityTagsDescription;
  }

  private static DeleteEntityTagsDescription deleteEntityTagsDescription(String cloudProvider,
                                                                         String accountId,
                                                                         String region,
                                                                         String entityType,
                                                                         String entityId,
                                                                         List<String> tags) {
    EntityRefIdBuilder.EntityRefId entityRefId = EntityRefIdBuilder.buildId(
      cloudProvider, entityType, entityId, accountId, region
    );

    DeleteEntityTagsDescription deleteEntityTagsDescription = new DeleteEntityTagsDescription();
    deleteEntityTagsDescription.setId(entityRefId.id);
    deleteEntityTagsDescription.setDeleteAll(true);
    deleteEntityTagsDescription.setTags(tags);

    return deleteEntityTagsDescription;
  }
}
