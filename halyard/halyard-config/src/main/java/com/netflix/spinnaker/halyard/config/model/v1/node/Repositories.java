/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.netflix.spinnaker.halyard.config.model.v1.repository.artifactory.ArtifactoryRepository;
import com.netflix.spinnaker.halyard.config.model.v1.repository.nexus.NexusRepository;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class Repositories extends Node implements Cloneable {
  ArtifactoryRepository artifactory = new ArtifactoryRepository();
  NexusRepository nexus = new NexusRepository();

  @Override
  public String getNodeName() {
    return "repository";
  }

  public boolean repositoryEnabled() {
    NodeIterator iterator = getChildren();
    Repository child = (Repository) iterator.getNext();
    while (child != null) {
      if (child.isEnabled()) {
        return true;
      }

      child = (Repository) iterator.getNext();
    }

    return false;
  }

  public static Class<? extends Repository> translateReposiroryType(String repositoryName) {
    Optional<? extends Class<?>> res =
        Arrays.stream(Repositories.class.getDeclaredFields())
            .filter(f -> f.getName().equals(repositoryName))
            .map(Field::getType)
            .findFirst();

    if (res.isPresent()) {
      return (Class<? extends Repository>) res.get();
    } else {
      throw new IllegalArgumentException(
          "No repository service with name \"" + repositoryName + "\" handled by halyard");
    }
  }

  public static Class<? extends Search> translateSearchType(String repositoryName) {
    Class<? extends Repository> repositoryClass = translateReposiroryType(repositoryName);

    String searchClassName = repositoryClass.getName().replaceAll("Repository", "Search");
    try {
      return (Class<? extends Search>) Class.forName(searchClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "No search for class \"" + searchClassName + "\" found", e);
    }
  }
}
