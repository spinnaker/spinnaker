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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO;
import org.springframework.beans.factory.annotation.Autowired;
import rx.Scheduler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class S3PipelineStrategyDAO extends S3Support<Pipeline> implements PipelineStrategyDAO {
  public S3PipelineStrategyDAO(ObjectMapper objectMapper,
                               AmazonS3 amazonS3,
                               Scheduler scheduler,
                               int refreshIntervalMs,
                               String bucket,
                               String rootFolder) {
    super(objectMapper, amazonS3, scheduler, refreshIntervalMs, bucket, (rootFolder + "/pipeline-strategies/").replaceAll("//", "/"));
  }

  @Override
  public String getPipelineId(String application, String pipelineName) {
    Pipeline matched = getPipelinesByApplication(application)
        .stream()
        .filter(pipeline -> pipeline.getName().equalsIgnoreCase(pipelineName))
        .findFirst()
        .orElseThrow(() -> new NotFoundException(
            String.format("No pipeline strategy found with name '%s' in application '%s'", pipelineName, application)
        ));

    return matched.getId();
  }

  @Override
  public Collection<Pipeline> getPipelinesByApplication(String application) {
    refresh();
    return all()
        .stream()
        .filter(pipelineStrategy -> pipelineStrategy.getApplication().equalsIgnoreCase(application))
        .collect(Collectors.toList());
  }

  @Override
  public Pipeline create(String id, Pipeline item) {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
    item.setId(id);

    update(id, item);
    return findById(id);
  }

  @Override
  public String getMetadataFilename() {
    return "pipeline-strategy-metadata.json";
  }

  @Override
  Class<Pipeline> getSerializedClass() {
    return Pipeline.class;
  }
}
