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

package com.netflix.spinnaker.front50.model;

import com.amazonaws.services.cloudtrail.model.UnsupportedOperationException;
import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.model.project.Project;
import com.netflix.spinnaker.front50.model.project.ProjectDAO;
import rx.Scheduler;

import java.util.*;

public class S3ProjectDAO extends S3Support<Project> implements ProjectDAO {
  public S3ProjectDAO(ObjectMapper objectMapper,
                      AmazonS3 amazonS3,
                      Scheduler scheduler,
                      int refreshIntervalMs,
                      String bucket,
                      String rootFolder) {
    super(objectMapper, amazonS3, scheduler, refreshIntervalMs, bucket, (rootFolder + "/projects/").replaceAll("//", "/"));
  }

  @Override
  public Project findByName(String name) throws NotFoundException {
    return fetchAllItems(allItemsCache.get())
        .stream()
        .filter(project -> project.getName().equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow(() -> new NotFoundException(String.format("No project found with name of %s", name)));
  }

  @Override
  public Project create(String id, Project item) {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
    item.setId(id);

    update(id, item);
    return findById(id);
  }

  @Override
  public void truncate() {}

  @Override
  String getMetadataFilename() {
    return "project-metadata.json";
  }

  @Override
  Class<Project> getSerializedClass() {
    return Project.class;
  }
}
