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

package com.netflix.spinnaker.clouddriver.entitytags.ops;

import static java.lang.String.format;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.entitytags.descriptions.DeleteEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.model.EntityTagsProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DeleteEntityTagsAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ENTITY_TAGS";

  private final EntityTagsProvider entityTagsProvider;
  private final DeleteEntityTagsDescription entityTagsDescription;

  public DeleteEntityTagsAtomicOperation(
      EntityTagsProvider entityTagsProvider, DeleteEntityTagsDescription entityTagsDescription) {
    this.entityTagsProvider = entityTagsProvider;
    this.entityTagsDescription = entityTagsDescription;
  }

  @Override
  public Void operate(List priorOutputs) {
    String id = entityTagsDescription.getId();

    getTask().updateStatus(BASE_PHASE, format("Retrieving %s from entity tags provider", id));
    Optional<EntityTags> currentOpt = entityTagsProvider.get(id);

    if (!currentOpt.isPresent()) {
      getTask().updateStatus(BASE_PHASE, format("Did not find %s in entity tags provider", id));
      getTask().updateStatus(BASE_PHASE, format("Deleting %s from entity tags provider", id));
      entityTagsProvider.delete(id);
      getTask().updateStatus(BASE_PHASE, format("Deleted %s from entity tags provider", id));
      return null;
    }

    EntityTags currentTags = currentOpt.get();

    Collection<String> currentTagNames =
        currentTags.getTags().stream()
            .map(EntityTags.EntityTag::getName)
            .collect(Collectors.toSet());

    List<String> requestedTags = entityTagsDescription.getTags();
    if (entityTagsDescription.isDeleteAll()
        || requestedTags == null
        || requestedTags.isEmpty()
        || requestedTags.containsAll(currentTagNames)) {
      getTask().updateStatus(BASE_PHASE, format("Deleting %s from entity tags provider", id));
      entityTagsProvider.delete(id);
      getTask().updateStatus(BASE_PHASE, format("Deleted %s from entity tags provider", id));
      return null;
    }

    getTask()
        .updateStatus(BASE_PHASE, format("Removing tags from %s (tags: %s)", id, requestedTags));
    requestedTags.forEach(currentTags::removeEntityTag);

    entityTagsProvider.index(currentTags);

    getTask().updateStatus(BASE_PHASE, format("Updated %s in entity tags provider", id));

    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
