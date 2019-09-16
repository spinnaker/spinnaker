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

import com.netflix.spinnaker.clouddriver.config.ExceptionClassifierConfigurationProperties;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

/**
 * Utility class to allow classifying non-SpinnakerException classes according to different
 * pre-determined characteristics.
 */
@Component
public class ExceptionClassifier {

  private final ExceptionClassifierConfigurationProperties properties;

  public ExceptionClassifier(ExceptionClassifierConfigurationProperties properties) {
    this.properties = properties;
  }

  /** Returns whether or not a given Exception is retryable or not. */
  public boolean isRetryable(@Nonnull Exception e) {
    if (e instanceof SpinnakerException) {
      return Optional.ofNullable(((SpinnakerException) e).getRetryable()).orElse(false);
    }
    return !properties.getNonRetryableClasses().contains(e.getClass().getName());
  }
}
