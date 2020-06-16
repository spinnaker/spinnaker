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
 *
 */

package com.netflix.spinnaker.kork.plugins.api;

import com.netflix.spinnaker.kork.annotations.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Denotes that a class provides plugin configuration. Classes annotated with [PluginConfiguration]
 * can be injected via the Plugin constructor or the Extension Point constructor.
 */
@Beta
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface PluginConfiguration {

  /**
   * The property value of the configuration.
   *
   * <p>If the configuration is for an extension point and the config is `stage-extension`, the
   * corresponding config coordinates would be:
   *
   * <p>`spinnaker.extensibility.plugins.pluginId.extensions.stage-extension.config`
   *
   * <p>If the configuration is for an plugin the config is `http-client`, the corresponding config
   * coordinates would be:
   *
   * <p>`spinnaker.extensibility.plugins.pluginId.http-client.config`
   *
   * @return
   */
  String value() default "";
}
