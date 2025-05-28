/*
 * Copyright (c) 2019 Schibsted Media Group.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@Component
public class DeployCloudFormationTask implements CloudProviderAware, Task {

  @Autowired KatoService katoService;

  @Autowired OortService oortService;

  @Autowired ObjectMapper objectMapper;

  @Autowired ArtifactUtils artifactUtils;

  public static final String TASK_NAME = "deployCloudFormation";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    String cloudProvider = getCloudProvider(stage);

    Map<String, Object> task = new HashMap<>(stage.getContext());
    if (!StringUtils.isNotBlank((String) task.get("source"))) {
      throw new IllegalArgumentException(
          "Field 'source' is missing and must be present in the stage context.");
    }

    if (task.get("source").equals("artifact")) {
      if (!StringUtils.isNotBlank((String) task.get("stackArtifactId"))
          && task.get("stackArtifact") == null) {
        throw new IllegalArgumentException(
            "Invalid stage format, no stack template artifact was specified.");
      }
      if (!StringUtils.isNotBlank((String) task.get("stackArtifactAccount"))
          && task.get("stackArtifact") == null) {
        throw new IllegalArgumentException(
            "Invalid stage format, no artifact account was specified.");
      }
      String stackArtifactId =
          Optional.ofNullable(task.get("stackArtifactId")).map(Object::toString).orElse(null);
      Artifact stackArtifact =
          Optional.ofNullable(task.get("stackArtifact"))
              .map(m -> objectMapper.convertValue(m, Artifact.class))
              .orElse(null);
      Artifact artifact =
          ArtifactUtils.withAccount(
              artifactUtils.getBoundArtifactForStage(stage, stackArtifactId, stackArtifact),
              Optional.ofNullable(task.get("stackArtifactAccount"))
                  .map(Object::toString)
                  .orElse(null));

      if (StringUtils.startsWith(artifact.getReference(), "s3://")) {
        log.debug("Using templateURL for stack template at: {}", artifact.getReference());
        task.put(
            "templateURL",
            StringUtils.replace(artifact.getReference(), "s3://", "https://s3.amazonaws.com/"));

      } else {
        try (ResponseBody responseBody =
            Retrofit2SyncCall.execute(oortService.fetchArtifact(artifact))) {
          String template = responseBody.string();
          log.debug("Fetched template from artifact {}: {}", artifact.getReference(), template);
          task.put("templateBody", template);
        } catch (IOException e) {
          throw new IllegalArgumentException(
              "Failed to read template from artifact definition " + artifact, e);
        }
      }
    }

    Object templateBody = task.get("templateBody");

    if (templateBody instanceof Map && !((Map) templateBody).isEmpty()) {
      templateBody = new Yaml().dump(templateBody);
      task.put("templateBody", templateBody);
    } else if (templateBody instanceof List && !((List) templateBody).isEmpty()) {
      templateBody =
          ((List<?>) templateBody)
              .stream().map(part -> new Yaml().dump(part)).collect(Collectors.joining("\n---\n"));
      task.put("templateBody", templateBody);
    }

    if (!task.containsKey("templateURL")
        && (!(templateBody instanceof String) || Strings.isNullOrEmpty((String) templateBody))) {
      throw new IllegalArgumentException(
          "Invalid stage format, missing artifact or templateBody field: "
              + templateBody
              + ", "
              + stage.getContext());
    }

    List<String> regions = (List<String>) task.get("regions");
    String region =
        regions.stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No regions selected. At least one region must be chosen."));
    task.put("region", region);

    Map<String, Map> operation =
        new ImmutableMap.Builder<String, Map>().put(TASK_NAME, task).build();

    TaskId taskId =
        katoService.requestOperations(cloudProvider, Collections.singletonList(operation));

    Map<String, Object> context =
        new ImmutableMap.Builder<String, Object>()
            .put("kato.result.expected", true)
            .put("kato.last.task.id", taskId)
            .build();

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
  }
}
