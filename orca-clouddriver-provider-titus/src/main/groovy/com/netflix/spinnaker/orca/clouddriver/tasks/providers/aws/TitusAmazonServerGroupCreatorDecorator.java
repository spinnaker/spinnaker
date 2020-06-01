/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws;

import static java.lang.String.format;

import com.google.common.base.Strings;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger;
import com.netflix.spinnaker.orca.pipeline.model.DockerTrigger;
import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
public class TitusAmazonServerGroupCreatorDecorator implements AmazonServerGroupCreatorDecorator {

  @Override
  public boolean supports(String cloudProvider) {
    return "titus".equalsIgnoreCase(cloudProvider);
  }

  @Override
  public void modifyCreateServerGroupOperationContext(
      StageExecution stage, Map<String, Object> context) {
    if (Strings.isNullOrEmpty((String) context.get("imageId"))) {
      getImageId(stage).ifPresent(imageId -> context.put("imageId", imageId));
    }

    Optional.ofNullable(stage.getExecution().getAuthentication())
        .map(PipelineExecution.AuthenticationDetails::getUser)
        .ifPresent(u -> context.put("user", u));
  }

  private Optional<String> getImageId(StageExecution stage) {
    Trigger trigger = stage.getExecution().getTrigger();
    if (trigger instanceof DockerTrigger) {
      DockerTrigger t = (DockerTrigger) trigger;
      return Optional.of(format("%s:%s", t.getRepository(), t.getTag()));
    }

    // This logic is bad because the original code was also awful.
    String imageId = null;
    if (trigger.getParameters().containsKey("imageName")) {
      imageId = (String) trigger.getParameters().get("imageName");
    }
    if (Strings.isNullOrEmpty(imageId) && trigger instanceof JenkinsTrigger) {
      JenkinsTrigger t = (JenkinsTrigger) trigger;
      imageId = (String) t.getProperties().getOrDefault("imageName", null);
    }
    if (Strings.isNullOrEmpty(imageId)) {
      imageId = (String) trigger.getOther().get("imageName");
    }

    return Optional.ofNullable(imageId);
  }
}
