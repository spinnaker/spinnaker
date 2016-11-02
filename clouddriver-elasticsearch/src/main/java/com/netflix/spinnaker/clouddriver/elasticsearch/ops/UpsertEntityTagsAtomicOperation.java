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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.elasticsearch.EntityRefIdBuilder;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.UpsertEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import io.searchbox.client.JestClient;

import java.util.List;

import static java.lang.String.format;

public class UpsertEntityTagsAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ENTITY_TAGS";

  private final Front50Service front50Service;
  private final ElasticSearchEntityTagsProvider entityTagsProvider;
  private final UpsertEntityTagsDescription entityTagsDescription;

  public UpsertEntityTagsAtomicOperation(Front50Service front50Service,
                                         ElasticSearchEntityTagsProvider entityTagsProvider,
                                         UpsertEntityTagsDescription tagEntityDescription) {
    this.front50Service = front50Service;
    this.entityTagsProvider = entityTagsProvider;
    this.entityTagsDescription = tagEntityDescription;
  }

  public Void operate(List priorOutputs) {
    if (entityTagsDescription.getId() == null) {
      EntityTags.EntityRef entityRef = entityTagsDescription.getEntityRef();
      EntityRefIdBuilder.EntityRefId entityRefId = EntityRefIdBuilder.buildId(
        entityRef.getCloudProvider(),
        entityRef.getEntityType(),
        entityRef.getEntityId(),
        (String) entityRef.attributes().get("account"),
        (String) entityRef.attributes().get("region")
      );
      entityTagsDescription.setId(entityRefId.id);
      entityTagsDescription.setIdPattern(entityRefId.idPattern);
    }

    getTask().updateStatus(
      BASE_PHASE,
      format("Tagging %s with %s", entityTagsDescription.getId(), entityTagsDescription.getTags())
    );

    EntityTags durableEntityTags = front50Service.saveEntityTags(entityTagsDescription);
    getTask().updateStatus(BASE_PHASE, format("Tagged %s in Front50", durableEntityTags.getId()));

    entityTagsDescription.setLastModified(durableEntityTags.getLastModified());
    entityTagsDescription.setLastModifiedBy(durableEntityTags.getLastModifiedBy());

    entityTagsProvider.index(entityTagsDescription);
    entityTagsProvider.verifyIndex(entityTagsDescription);

    getTask().updateStatus(BASE_PHASE, format("Indexed %s in ElasticSearch", entityTagsDescription.getId()));
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
