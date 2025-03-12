/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.orchestration;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.netflix.spinnaker.clouddriver.config.ExceptionClassifierConfigurationProperties;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Utility class to allow classifying non-SpinnakerException classes according to different
 * pre-determined characteristics.
 */
@Component
@Slf4j
public class ExceptionClassifier {

  private final ExceptionClassifierConfigurationProperties properties;
  private final DynamicConfigService dynamicConfigService;

  public ExceptionClassifier(
      ExceptionClassifierConfigurationProperties properties,
      DynamicConfigService dynamicConfigService) {
    this.properties = properties;
    this.dynamicConfigService = dynamicConfigService;
  }

  /** Returns whether or not a given Exception is retryable or not. */
  public boolean isRetryable(@Nonnull Exception e) {
    if (e instanceof SpinnakerException) {
      return Optional.ofNullable(((SpinnakerException) e).getRetryable()).orElse(false);
    }

    boolean retryable = false;
    try {
      String dynamicRetraybleClasses =
          dynamicConfigService.getConfig(
              String.class,
              "clouddriver.exception-classifier.retryable-exceptions",
              String.join(",", properties.getRetryableClasses()));

      if (dynamicRetraybleClasses != null) {
        List<String> dynamicRetraybleClassesList =
            Lists.newArrayList(Splitter.on(",").split(dynamicRetraybleClasses));

        List<String> retryableClasses =
            Stream.of(dynamicRetraybleClassesList, properties.getRetryableClasses())
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        retryable = retryableClasses.contains(e.getClass().getName());
      } else {
        retryable = properties.getRetryableClasses().contains(e.getClass().getName());
      }
    } catch (Exception caughtException) {
      log.error("Unexpected exception while processing retryable classes", caughtException);
    }

    log.trace("Evaluated retryable status for {} to '{}'", e.getClass().getName(), retryable);

    return retryable;
  }
}
