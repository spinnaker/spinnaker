/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch.ops;

import static com.netflix.spinnaker.clouddriver.elasticsearch.ops.BulkUpsertEntityTagsAtomicOperation.entityRefId;

import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.BulkUpsertEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.UpsertEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.kork.core.RetrySupport;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class UpsertEntityTagsAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ENTITY_TAGS";

  private final RetrySupport retrySupport;
  private final Front50Service front50Service;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final ElasticSearchEntityTagsProvider entityTagsProvider;
  private final UpsertEntityTagsDescription entityTagsDescription;

  public UpsertEntityTagsAtomicOperation(
      RetrySupport retrySupport,
      Front50Service front50Service,
      AccountCredentialsProvider accountCredentialsProvider,
      ElasticSearchEntityTagsProvider entityTagsProvider,
      UpsertEntityTagsDescription tagEntityDescription) {
    this.retrySupport = retrySupport;
    this.front50Service = front50Service;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.entityTagsProvider = entityTagsProvider;
    this.entityTagsDescription = tagEntityDescription;
  }

  public Void operate(List priorOutputs) {
    BulkUpsertEntityTagsDescription bulkDescription = new BulkUpsertEntityTagsDescription();
    bulkDescription.isPartial = entityTagsDescription.isPartial;
    bulkDescription.entityTags = Collections.singletonList(entityTagsDescription);

    getTask()
        .updateStatus(
            BASE_PHASE,
            String.format(
                "Updating entity tags for %s (isPartial: %s, tags: %s)",
                Optional.ofNullable(entityTagsDescription.getId())
                    .orElse(entityRefId(accountCredentialsProvider, entityTagsDescription).id),
                entityTagsDescription.isPartial,
                entityTagsDescription.getTags().stream()
                    .map(EntityTags.EntityTag::getName)
                    .collect(Collectors.joining(", "))));

    new BulkUpsertEntityTagsAtomicOperation(
            retrySupport,
            front50Service,
            accountCredentialsProvider,
            entityTagsProvider,
            bulkDescription)
        .operate(priorOutputs);

    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
