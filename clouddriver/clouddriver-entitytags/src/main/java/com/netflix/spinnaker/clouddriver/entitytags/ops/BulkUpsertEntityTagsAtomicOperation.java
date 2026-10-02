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

package com.netflix.spinnaker.clouddriver.entitytags.ops;

import static java.lang.String.format;

import com.google.common.collect.Lists;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.entitytags.EntityRefIdBuilder;
import com.netflix.spinnaker.clouddriver.entitytags.descriptions.BulkUpsertEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.model.EntityTagsProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BulkUpsertEntityTagsAtomicOperation
    implements AtomicOperation<BulkUpsertEntityTagsAtomicOperationResult> {
  private static final Logger log =
      LoggerFactory.getLogger(BulkUpsertEntityTagsAtomicOperation.class);
  private static final String BASE_PHASE = "ENTITY_TAGS";

  private final AccountCredentialsProvider accountCredentialsProvider;
  private final EntityTagsProvider entityTagsProvider;
  private final BulkUpsertEntityTagsDescription bulkUpsertEntityTagsDescription;

  public BulkUpsertEntityTagsAtomicOperation(
      AccountCredentialsProvider accountCredentialsProvider,
      EntityTagsProvider entityTagsProvider,
      BulkUpsertEntityTagsDescription bulkUpsertEntityTagsDescription) {
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
    String user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");

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

              modifiedEntityTags.forEach(
                  tag -> {
                    tag.setLastModified(now.getTime());
                    tag.setLastModifiedBy(user);
                  });

              getTask().updateStatus(BASE_PHASE, "Indexing tags in entity tags provider");
              try {
                entityTagsProvider.bulkIndex(modifiedEntityTags);
                result.upserted.addAll(modifiedEntityTags);
              } catch (Exception e) {
                log.error("Failed to bulkIndex entity tags", e);
                modifiedEntityTags.forEach(
                    tag ->
                        result.failures.add(
                            new BulkUpsertEntityTagsAtomicOperationResult.UpsertFailureResult(
                                tag, e)));
              }
            });
    return result;
  }

  private Map<String, EntityTags> retrieveExistingTags(List<EntityTags> entityTags) {
    Map<String, EntityTags> existing = new HashMap<>();
    for (EntityTags tag : entityTags) {
      String id = tag.getId();
      if (id == null) {
        continue;
      }
      try {
        entityTagsProvider.get(id).ifPresent(current -> existing.put(id, current));
      } catch (Exception e) {
        log.error(
            "Unable to retrieve existing tag from entity tags provider, reason: {} (id: {})",
            e.getMessage(),
            id);
        throw e;
      }
    }
    return existing;
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

  /**
   * @return true if all {@code target} tags are contained in {@code source}, otherwise false
   */
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
