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

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.parser.ParserException;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

@Slf4j
@Component
public class DeployCloudFormationTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired
  KatoService katoService;

  @Autowired
  OortService oortService;

  @Autowired
  ArtifactResolver artifactResolver;

  public static final String TASK_NAME = "deployCloudFormation";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    String cloudProvider = getCloudProvider(stage);

    Map<String, Object> task = new HashMap<>(stage.getContext());
    if (!StringUtils.isNotBlank((String) task.get("source"))) {
      throw new IllegalArgumentException("Field 'source' is missing and must be present in the stage context.");
    }

    if (task.get("source").equals("artifact")) {
      if (!StringUtils.isNotBlank((String) task.get("stackArtifactId"))) {
        throw new IllegalArgumentException("Invalid stage format, no stack template artifact was specified.");
      }
      if (!StringUtils.isNotBlank((String) task.get("stackArtifactAccount"))) {
        throw new IllegalArgumentException("Invalid stage format, no artifact account was specified.");
      }
      Artifact artifact = artifactResolver.getBoundArtifactForId(stage, task.get("stackArtifactId").toString());
      artifact.setArtifactAccount(task.get("stackArtifactAccount").toString());
      Response response = oortService.fetchArtifact(artifact);
      try {
        String template = CharStreams.toString(new InputStreamReader(response.getBody().in()));
        log.debug("Fetched template from artifact {}: {}", artifact.getReference(), template);
        // attempt to deserialize template body to a map. supports YAML or JSON formatted templates.
        Map<String, Object> templateBody = (Map<String, Object>) new Yaml().load(template);
        task.put("templateBody", templateBody);
      } catch (IOException e) {
        throw new IllegalArgumentException("Failed to read template from artifact definition "+ artifact, e);
      } catch (ParserException e) {
        throw new IllegalArgumentException("Template body must be valid JSON or YAML.", e);
      }
    }

    Map templateBody = (Map) task.get("templateBody");
    if (templateBody == null || templateBody.isEmpty()) {
      throw new IllegalArgumentException(
        "Invalid stage format, missing artifact or templateBody field: " + templateBody + ", " + stage.getContext());
    }

    List<String> regions = (List<String>) task.get("regions");
    String region = regions
      .stream()
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("No regions selected. At least one region must be chosen."));
    task.put("region", region);

    Map<String, Map> operation = new ImmutableMap.Builder<String, Map>()
        .put(TASK_NAME, task)
        .build();

    TaskId taskId = katoService.requestOperations(cloudProvider, Collections.singletonList(operation))
      .toBlocking().first();

    Map<String, Object> context = new ImmutableMap.Builder<String, Object>()
        .put("kato.result.expected", true)
        .put("kato.last.task.id", taskId)
        .build();

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
  }

}
