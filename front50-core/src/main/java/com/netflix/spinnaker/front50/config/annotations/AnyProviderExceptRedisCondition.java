/*
 * Copyright 2020 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.front50.config.annotations;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class AnyProviderExceptRedisCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    Environment env = context.getEnvironment();
    return serviceEnabled("sql", env)
        || serviceEnabled("s3", env)
        || serviceEnabled("oracle", env)
        || serviceEnabled("gcs", env)
        || serviceEnabled("azs", env)
        || serviceEnabled("swift", env);
  }

  private static boolean serviceEnabled(String s, Environment env) {
    return env.getProperty(
        (s.equals("sql") ? "" : "spinnaker.") + s + ".enabled", Boolean.class, false);
  }
}
