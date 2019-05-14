/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.elasticsearch.ops;

import static java.lang.String.format;

import com.google.common.collect.Lists;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.elasticsearch.EntityRefIdBuilder;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.BulkUpsertEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BulkUpsertEntityTagsAtomicOperation
    implements AtomicOperation<BulkUpsertEntityTagsAtomicOperationResult> {
  private static final Logger log =
      LoggerFactory.getLogger(BulkUpsertEntityTagsAtomicOperation.class);
  private static final String BASE_PHASE = "ENTITY_TAGS";

  private final RetrySupport retrySupport;
  private final Front50Service front50Service;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final ElasticSearchEntityTagsProvider entityTagsProvider;
  private final BulkUpsertEntityTagsDescription bulkUpsertEntityTagsDescription;

  public BulkUpsertEntityTagsAtomicOperation(
      RetrySupport retrySupport,
      Front50Service front50Service,
      AccountCredentialsProvider accountCredentialsProvider,
      ElasticSearchEntityTagsProvider entityTagsProvider,
      BulkUpsertEntityTagsDescription bulkUpsertEntityTagsDescription) {
    this.retrySupport = retrySupport;
    this.front50Service = front50Service;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.entityTagsProvider = entityTagsProvider;
    this.bulkUpsertEntityTagsDescription = bulkUpsertEntityTagsDescription;
  }

  public BulkUpsertEntityTagsAtomicOperationResult operate(List priorOutputs) {
    BulkUpsertEntityTagsAtomicOperationResult result =
        new BulkUpsertEntityTagsAtomicOperationResult();

    if (bulkUpsertEntityTagsDescription.entityTags != null) {
      // ensure that this collection is _not_ unmodifiable
      bulkUpsertEntityTagsDescription.entityTags =
          new ArrayList<>(bulkUpsertEntityTagsDescription.entityTags);
    } else {
      bulkUpsertEntityTagsDescription.entityTags = new ArrayList<>();
    }

    List<EntityTags> entityTags = bulkUpsertEntityTagsDescription.entityTags;
    addTagIdsIfMissing(entityTags, result);

    mergeTags(bulkUpsertEntityTagsDescription);

    Date now = new Date();

    Lists.partition(entityTags, 50)
        .forEach(
            tags -> {
              getTask().updateStatus(BASE_PHASE, "Retrieving current entity tags");
              Map<String, EntityTags> existingTags = retrieveExistingTags(tags);

              List<EntityTags> modifiedEntityTags = new ArrayList<>();
              getTask().updateStatus(BASE_PHASE, "Merging existing tags and metadata");
              tags.forEach(
                  tag -> {
                    boolean wasModified =
                        mergeExistingTagsAndMetadata(
                            now,
                            existingTags.get(tag.getId()),
                            tag,
                            bulkUpsertEntityTagsDescription.isPartial);

                    if (wasModified) {
                      modifiedEntityTags.add(tag);
                    }
                  });

              if (modifiedEntityTags.isEmpty()) {
                getTask().updateStatus(BASE_PHASE, "No tags have been modified");
                return;
              }

              getTask()
                  .updateStatus(BASE_PHASE, "Performing batch update to durable tagging service");
              Map<String, EntityTags> durableTags =
                  front50Service.batchUpdate(new ArrayList<>(modifiedEntityTags)).stream()
                      .collect(Collectors.toMap(EntityTags::getId, Function.identity()));

              getTask().updateStatus(BASE_PHASE, "Pushing tags to Elastic Search");
              updateMetadataFromDurableTagsAndIndex(modifiedEntityTags, durableTags, result);
              result.upserted.addAll(modifiedEntityTags);
            });
    return result;
  }

  private Map<String, EntityTags> retrieveExistingTags(List<EntityTags> entityTags) {
    List<String> ids = entityTags.stream().map(EntityTags::getId).collect(Collectors.toList());

    try {
      return retrySupport.retry(
          () ->
              front50Service.getAllEntityTagsById(ids).stream()
                  .collect(Collectors.toMap(EntityTags::getId, Function.identity())),
          10,
          2000,
          false);
    } catch (Exception e) {
      log.error(
          "Unable to retrieve existing tags from Front50, reason: {} (ids: {})",
          e.getMessage(),
          ids);
      throw e;
    }
  }

  private void addTagIdsIfMissing(
      List<EntityTags> entityTags, BulkUpsertEntityTagsAtomicOperationResult result) {
    Collection<EntityTags> failed = new ArrayList<>();
    entityTags.forEach(
        tag -> {
          if (tag.getId() == null) {
            try {
              EntityRefIdBuilder.EntityRefId entityRefId =
                  entityRefId(accountCredentialsProvider, tag);
              tag.setId(entityRefId.id);
              tag.setIdPattern(entityRefId.idPattern);
            } catch (Exception e) {
              log.error("Failed to build tag id for {}", tag.getId(), e);
              getTask()
                  .updateStatus(
                      BASE_PHASE,
                      format(
                          "Failed to build tag id for %s, reason: %s",
                          tag.getId(), e.getMessage()));
              failed.add(tag);
              result.failures.add(
                  new BulkUpsertEntityTagsAtomicOperationResult.UpsertFailureResult(tag, e));
            }
          }
        });
    entityTags.removeAll(failed);
  }

  private void updateMetadataFromDurableTagsAndIndex(
      List<EntityTags> entityTags,
      Map<String, EntityTags> durableTags,
      BulkUpsertEntityTagsAtomicOperationResult result) {
    Collection<EntityTags> failed = new ArrayList<>();
    entityTags.forEach(
        tag -> {
          try {
            EntityTags durableTag = durableTags.get(tag.getId());
            tag.setLastModified(durableTag.getLastModified());
            tag.setLastModifiedBy(durableTag.getLastModifiedBy());
          } catch (Exception e) {
            log.error("Failed to update {} in ElasticSearch", tag.getId(), e);
            getTask()
                .updateStatus(
                    BASE_PHASE,
                    format(
                        "Failed to update %s in ElasticSearch, reason: %s",
                        tag.getId(), e.getMessage()));
            failed.add(tag);
            result.failures.add(
                new BulkUpsertEntityTagsAtomicOperationResult.UpsertFailureResult(tag, e));
          }
        });
    entityTags.removeAll(failed);
    getTask().updateStatus(BASE_PHASE, "Indexing tags in ElasticSearch");
    entityTagsProvider.bulkIndex(entityTags);

    entityTags.forEach(
        tag -> {
          try {
            entityTagsProvider.verifyIndex(tag);
          } catch (Exception e) {
            log.error("Failed to verify {} in ElasticSearch", tag.getId(), e);
            getTask()
                .updateStatus(
                    BASE_PHASE,
                    format(
                        "Failed to verify %s in ElasticSearch, reason: %s",
                        tag.getId(), e.getMessage()));
            failed.add(tag);
          }
        });
    entityTags.removeAll(failed);
  }

  public static EntityRefIdBuilder.EntityRefId entityRefId(
      AccountCredentialsProvider accountCredentialsProvider, EntityTags description) {
    EntityTags.EntityRef entityRef = description.getEntityRef();
    String entityRefAccount = entityRef.getAccount();
    String entityRefAccountId = entityRef.getAccountId();

    if (entityRefAccount != null && !entityRefAccount.equals("*") && entityRefAccountId == null) {
      // add `accountId` if not explicitly provided
      AccountCredentials accountCredentials =
          lookupAccountCredentialsByAccountIdOrName(
              accountCredentialsProvider, entityRefAccount, "accountName");
      entityRefAccountId = accountCredentials.getAccountId();
      entityRef.setAccountId(entityRefAccountId);
    }

    if (entityRefAccount == null && entityRefAccountId != null) {
      // add `account` if not explicitly provided
      AccountCredentials accountCredentials =
          lookupAccountCredentialsByAccountIdOrName(
              accountCredentialsProvider, entityRefAccountId, "accountId");
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
        entityRef.getRegion());
  }

  public static boolean mergeExistingTagsAndMetadata(
      Date now, EntityTags currentTags, EntityTags updatedTags, boolean isPartial) {
    if (currentTags == null) {
      addTagMetadata(now, updatedTags);
      return true;
    }

    // a modification if at least one updated entity tag is not contained within `currentTags`
    boolean wasModified = !containedWithin(currentTags, updatedTags);

    if (!isPartial) {
      // a modification if at least one current entity tag is not contained within `updatedTags`
      wasModified = wasModified || !containedWithin(updatedTags, currentTags);

      replaceTagContents(currentTags, updatedTags);
    }

    updatedTags.setTagsMetadata(
        currentTags.getTagsMetadata() == null ? new ArrayList<>() : currentTags.getTagsMetadata());

    updatedTags.getTags().forEach(tag -> updatedTags.putEntityTagMetadata(tagMetadata(tag, now)));

    currentTags.getTags().forEach(updatedTags::putEntityTagIfAbsent);

    return wasModified;
  }

  /** @return true if all {@code target} tags are contained in {@code source}, otherwise false */
  private static boolean containedWithin(EntityTags source, EntityTags target) {
    return target.getTags().stream()
        .allMatch(
            updatedTag ->
                source.getTags().stream()
                    .anyMatch(
                        currentTag ->
                            currentTag.getName().equals(updatedTag.getName())
                                && currentTag.getValue().equals(updatedTag.getValue())));
  }

  private static void mergeTags(BulkUpsertEntityTagsDescription bulkUpsertEntityTagsDescription) {
    List<EntityTags> toRemove = new ArrayList<>();
    bulkUpsertEntityTagsDescription.entityTags.forEach(
        tag -> {
          Collection<EntityTags> matches =
              bulkUpsertEntityTagsDescription.entityTags.stream()
                  .filter(
                      t -> t.getId().equals(tag.getId()) && !toRemove.contains(t) && !tag.equals(t))
                  .collect(Collectors.toList());
          if (matches.size() > 1) {
            matches.forEach(m -> tag.getTags().addAll(m.getTags()));
            toRemove.addAll(matches);
          }
        });
    bulkUpsertEntityTagsDescription.entityTags.removeAll(toRemove);
  }

  private static void replaceTagContents(EntityTags currentTags, EntityTags entityTagsDescription) {
    Map<String, EntityTags.EntityTag> entityTagsByName =
        entityTagsDescription.getTags().stream()
            .collect(Collectors.toMap(EntityTags.EntityTag::getName, x -> x));

    currentTags.setTags(entityTagsDescription.getTags());
    for (EntityTags.EntityTagMetadata entityTagMetadata : currentTags.getTagsMetadata()) {
      if (!entityTagsByName.containsKey(entityTagMetadata.getName())) {
        currentTags.removeEntityTagMetadata(entityTagMetadata.getName());
      }
    }
  }

  private static EntityTags.EntityTagMetadata tagMetadata(
      EntityTags.EntityTag entityTag, Date now) {
    String user = AuthenticatedRequest.getSpinnakerUser().orElse("unknown");

    String tagName = entityTag.getName();
    if (entityTag.getTimestamp() != null) {
      // entity tag has an explicit timestamp, favor it for last modified date
      now = new Date(entityTag.getTimestamp());
    }

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
    entityTags.getTags().forEach(tag -> entityTags.putEntityTagMetadata(tagMetadata(tag, now)));
  }

  private static AccountCredentials lookupAccountCredentialsByAccountIdOrName(
      AccountCredentialsProvider accountCredentialsProvider,
      String entityRefAccountIdOrName,
      String type) {
    return accountCredentialsProvider.getAll().stream()
        .filter(
            c ->
                entityRefAccountIdOrName.equals(c.getAccountId())
                    || entityRefAccountIdOrName.equals(c.getName()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format(
                        "No credentials found for %s: %s", type, entityRefAccountIdOrName)));
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
