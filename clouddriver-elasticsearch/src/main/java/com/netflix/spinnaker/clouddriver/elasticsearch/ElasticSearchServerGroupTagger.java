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
import com.netflix.spinnaker.clouddriver.tags.ServerGroupTagger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ElasticSearchServerGroupTagger implements ServerGroupTagger {
  private static final Logger log = LoggerFactory.getLogger(ElasticSearchServerGroupTagger.class);

  private static final String SERVER_GROUP_TYPE = "servergroup";
  private static final String ALERT_TYPE = "alert";
  private static final String ALERT_KEY_PREFIX = "spinnaker_ui_alert:";

  private final Front50Service front50Service;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final ElasticSearchEntityTagsProvider entityTagsProvider;

  public ElasticSearchServerGroupTagger(Front50Service front50Service,
                                        AccountCredentialsProvider accountCredentialsProvider,
                                        ElasticSearchEntityTagsProvider entityTagsProvider) {
    this.front50Service = front50Service;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.entityTagsProvider = entityTagsProvider;
  }

  @Override
  public void alert(String cloudProvider,
                    String accountId,
                    String region,
                    String serverGroupName,
                    String key,
                    String value) {
    UpsertEntityTagsAtomicOperation upsertEntityTagsAtomicOperation = new UpsertEntityTagsAtomicOperation(
      front50Service,
      accountCredentialsProvider,
      entityTagsProvider,
      upsertEntityTagsDescription(cloudProvider, accountId, region, serverGroupName, key, value)
    );

    try {
      TaskRepository.threadLocalTask.set(new DefaultTask(this.getClass().getSimpleName()));
      upsertEntityTagsAtomicOperation.operate(Collections.emptyList());
    } finally {
      TaskRepository.threadLocalTask.set(null);
    }
  }

  @Override
  public Collection<EntityTags> taggedEntities(String cloudProvider,
                                               String accountId,
                                               String tagName,
                                               int maxResults) {
    return entityTagsProvider.getAll(
      cloudProvider, ENTITY_TYPE, null, null, accountId, null, Collections.singletonMap(tagName, "*"), maxResults
    );
  }

  @Override
  public void deleteAll(String cloudProvider, String accountId, String region, String serverGroupName) {
    DeleteEntityTagsDescription deleteEntityTagsDescription = deleteEntityTagsDescription(
      cloudProvider,
      accountId,
      region,
      serverGroupName,
      null
    );
    log.info("Removing all entity tags for '{}'", deleteEntityTagsDescription.getId());

    delete(deleteEntityTagsDescription);
  }

  @Override
  public void delete(String cloudProvider, String accountId, String region, String serverGroupName, String tagName) {
    DeleteEntityTagsDescription deleteEntityTagsDescription = deleteEntityTagsDescription(
      cloudProvider,
      accountId,
      region,
      serverGroupName,
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
        Optional.ofNullable(originalTask).orElse(new DefaultTask(ElasticSearchServerGroupTagger.class.getSimpleName()))
      );
      run(deleteEntityTagsAtomicOperation);
    } finally {
      TaskRepository.threadLocalTask.set(originalTask);
    }
  }


  private static UpsertEntityTagsDescription upsertEntityTagsDescription(String cloudProvider,
                                                                         String accountId,
                                                                         String region,
                                                                         String serverGroupName,
                                                                         String key,
                                                                         String value) {
    EntityTags.EntityRef entityRef = new EntityTags.EntityRef();
    entityRef.setEntityType(SERVER_GROUP_TYPE);
    entityRef.setEntityId(serverGroupName);
    entityRef.setCloudProvider(cloudProvider);
    entityRef.setAccountId(accountId);
    entityRef.setRegion(region);

    Map<String, String> entityTagValue = new HashMap<>();
    entityTagValue.put("message", value);
    entityTagValue.put("type", ALERT_TYPE);

    EntityTags.EntityTag entityTag = new EntityTags.EntityTag();
    entityTag.setName(ALERT_KEY_PREFIX + key);
    entityTag.setValue(entityTagValue);
    entityTag.setValueType(EntityTags.EntityTagValueType.object);

    UpsertEntityTagsDescription upsertEntityTagsDescription = new UpsertEntityTagsDescription();
    upsertEntityTagsDescription.setEntityRef(entityRef);
    upsertEntityTagsDescription.setTags(Collections.singletonList(entityTag));

    return upsertEntityTagsDescription;
  }

  private static DeleteEntityTagsDescription deleteEntityTagsDescription(String cloudProvider,
                                                                         String accountId,
                                                                         String region,
                                                                         String serverGroupName,
                                                                         List<String> tags) {
    EntityRefIdBuilder.EntityRefId entityRefId = EntityRefIdBuilder.buildId(
      cloudProvider, "servergroup", serverGroupName, accountId, region
    );

    DeleteEntityTagsDescription deleteEntityTagsDescription = new DeleteEntityTagsDescription();
    deleteEntityTagsDescription.setId(entityRefId.id);
    deleteEntityTagsDescription.setDeleteAll(true);
    deleteEntityTagsDescription.setTags(tags);

    return deleteEntityTagsDescription;
  }
}
