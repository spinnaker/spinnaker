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

import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.elasticsearch.EntityRefIdBuilder;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.UpsertEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import retrofit.RetrofitError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class UpsertEntityTagsAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ENTITY_TAGS";

  private final Front50Service front50Service;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final ElasticSearchEntityTagsProvider entityTagsProvider;
  private final UpsertEntityTagsDescription entityTagsDescription;

  public UpsertEntityTagsAtomicOperation(Front50Service front50Service,
                                         AccountCredentialsProvider accountCredentialsProvider,
                                         ElasticSearchEntityTagsProvider entityTagsProvider,
                                         UpsertEntityTagsDescription tagEntityDescription) {
    this.front50Service = front50Service;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.entityTagsProvider = entityTagsProvider;
    this.entityTagsDescription = tagEntityDescription;
  }

  public Void operate(List priorOutputs) {
    if (entityTagsDescription.getId() == null) {
      EntityRefIdBuilder.EntityRefId entityRefId = entityRefId(accountCredentialsProvider, entityTagsDescription);

      entityTagsDescription.setId(entityRefId.id);
      entityTagsDescription.setIdPattern(entityRefId.idPattern);
    }

    Date now = new Date();

    EntityTags currentTags = null;
    try {
      getTask().updateStatus(BASE_PHASE, format("Retrieving current entity tags (%s)", entityTagsDescription.getId()));
      currentTags = front50Service.getEntityTags(entityTagsDescription.getId());
    } catch (RetrofitError e) {
      if (e.getResponse().getStatus() == 404) {
        getTask().updateStatus(
          BASE_PHASE,
          format("No existing tags found (%s)", entityTagsDescription.getId())
        );
      } else {
        throw e;
      }
    }

    if (currentTags != null && !entityTagsDescription.isPartial) {
      Map<String, EntityTags.EntityTag> entityTagsByName = entityTagsDescription.getTags().stream()
        .collect(Collectors.toMap(EntityTags.EntityTag::getName, x -> x));

      currentTags.setTags(entityTagsDescription.getTags());
      for (EntityTags.EntityTagMetadata entityTagMetadata : currentTags.getTagsMetadata()) {
        if (!entityTagsByName.containsKey(entityTagMetadata.getName())) {
          currentTags.removeEntityTagMetadata(entityTagMetadata.getName());
        }
      }
    }
    mergeExistingTagsAndMetadata(now, currentTags, entityTagsDescription);

    Collection<String> entityTagSummaries = entityTagsDescription.getTags().stream()
      .map(entityTag -> entityTag.getName() + ":" + entityTag.getValue())
      .collect(Collectors.toList());

    getTask().updateStatus(
      BASE_PHASE,
      format("Tagging %s with %s", entityTagsDescription.getId(), entityTagSummaries)
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

  private static EntityRefIdBuilder.EntityRefId entityRefId(AccountCredentialsProvider accountCredentialsProvider,
                                                            UpsertEntityTagsDescription description) {
    EntityTags.EntityRef entityRef = description.getEntityRef();
    String entityRefAccount = entityRef.getAccount();
    String entityRefAccountId = entityRef.getAccountId();

    if (entityRefAccount != null && entityRefAccountId == null) {
      // add `accountId` if not explicitly provided
      AccountCredentials accountCredentials = accountCredentialsProvider.getCredentials(entityRefAccount);
      entityRefAccountId = accountCredentials.getAccountId();
      entityRef.setAccountId(entityRefAccountId);
    }

    if (entityRefAccount == null && entityRefAccountId != null) {
      // add `account` if not explicitly provided
      AccountCredentials accountCredentials = lookupAccountCredentials(accountCredentialsProvider, entityRefAccountId);
      if (accountCredentials != null) {
        entityRefAccount = accountCredentials.getName();
        entityRef.setAccount(entityRefAccount);
      }
    }

    return EntityRefIdBuilder.buildId(
      entityRef.getCloudProvider(),
      entityRef.getEntityType(),
      entityRef.getEntityId(),
      Optional.ofNullable(entityRefAccountId).orElse(entityRefAccount),
      entityRef.getRegion()
    );
  }

  private static void mergeExistingTagsAndMetadata(Date now,
                                                   EntityTags currentTags,
                                                   EntityTags updatedTags) {
    if (currentTags == null) {
      addTagMetadata(now, updatedTags);
      return;
    }

    updatedTags.setTagsMetadata(
      currentTags.getTagsMetadata() == null ? new ArrayList<>() : currentTags.getTagsMetadata()
    );

    updatedTags.getTags().forEach(tag -> {
      updatedTags.putEntityTagMetadata(tagMetadata(tag.getName(), now));
    });

    currentTags.getTags().forEach(updatedTags::putEntityTagIfAbsent);
  }

  private static EntityTags.EntityTagMetadata tagMetadata(String tagName, Date now) {
    String user = AuthenticatedRequest.getSpinnakerUser().orElse("unknown");

    EntityTags.EntityTagMetadata metadata = new EntityTags.EntityTagMetadata();
    metadata.setName(tagName);
    metadata.setCreated(now.getTime());
    metadata.setLastModified(now.getTime());
    metadata.setCreatedBy(user);
    metadata.setLastModifiedBy(user);

    return metadata;
  }

  private static void addTagMetadata(Date now, EntityTags entityTags) {
    entityTags.setTagsMetadata(new ArrayList<>());
    entityTags.getTags().forEach(tag -> {
      entityTags.putEntityTagMetadata(tagMetadata(tag.getName(), now));
    });
  }

  private static AccountCredentials lookupAccountCredentials(AccountCredentialsProvider accountCredentialsProvider,
                                                             String entityRefAccountId) {
    return accountCredentialsProvider.getAll().stream()
      .filter(c -> entityRefAccountId.equals(c.getAccountId()))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("No credentials found for accountId '" + entityRefAccountId + "'"));
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
