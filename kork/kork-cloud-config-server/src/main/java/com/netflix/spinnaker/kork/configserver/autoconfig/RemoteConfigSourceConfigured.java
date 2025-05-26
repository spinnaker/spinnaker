/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.configserver.autoconfig;

import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class RemoteConfigSourceConfigured implements Condition {
  private static final String DEFAULT_REMOTE_REPO_TYPES = "default,git,vault,jdbc,credhub,awsS3";

  @Override
  public boolean matches(ConditionContext context, @NotNull AnnotatedTypeMetadata metadata) {
    ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
    if (beanFactory != null) {
      Environment environment = context.getEnvironment();
      String remoteRepoTypes =
          environment.getProperty(
              "spring.cloud.config.remote-repo-types", DEFAULT_REMOTE_REPO_TYPES);

      for (String remoteRepoType : StringUtils.split(remoteRepoTypes, ',')) {
        if (beanFactory.containsBean(remoteRepoType + "EnvironmentRepository")
            || beanFactory.containsBean(remoteRepoType + "-env-repo0")) {
          return true;
        }
      }
    }
    return false;
  }
}
