/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.postprocessors;

import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.services.IgorService;
import com.netflix.spinnaker.kork.core.RetrySupport;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Post-processor that looks up build details from Igor if a pipeline's trigger is associated
 * with a build (ie, has a master and build number). Any later post-processor that wants to reference
 * this build information should set its @Order to greater than 1.
 */
@Component
@ConditionalOnProperty("igor.enabled")
@Slf4j
public class BuildInfoPostProcessor implements PipelinePostProcessor {
  private IgorService igorService;
  private RetrySupport retrySupport;

  @Autowired
  BuildInfoPostProcessor(@NonNull IgorService igorService, @NonNull RetrySupport retrySupport) {
    this.igorService = igorService;
    this.retrySupport = retrySupport;
  }

  public Pipeline processPipeline(Pipeline inputPipeline) {
    Trigger inputTrigger = inputPipeline.getTrigger();
    if (inputTrigger == null) {
      return inputPipeline;
    }

    Trigger augmentedTrigger = addBuildInfo(inputTrigger);
    return inputPipeline.withTrigger(augmentedTrigger);
  }

  private Trigger addBuildInfo(@NonNull Trigger inputTrigger) {
    String master = inputTrigger.getMaster();
    Integer buildNumber = inputTrigger.getBuildNumber();
    String job = inputTrigger.getJob();
    String propertyFile = inputTrigger.getPropertyFile();

    Map<String, Object> buildInfo = null;
    Map<String, Object> properties = null;
    if (master != null && buildNumber != null && StringUtils.isNotEmpty(job)) {
      buildInfo = retry(() -> igorService.getBuild(buildNumber, master, job));
      if (StringUtils.isNotEmpty(propertyFile)) {
        properties = retry(() -> igorService.getPropertyFile(buildNumber, propertyFile, master, job));
      }
    }
    return inputTrigger.withBuildInfo(buildInfo).withProperties(properties);
  }

  private <T> T retry(Supplier<T> supplier) {
    return retrySupport.retry(supplier, 5, 1000, true);
  }

  public PostProcessorPriority priority() {
    return PostProcessorPriority.BUILD_INFO;
  }
}
