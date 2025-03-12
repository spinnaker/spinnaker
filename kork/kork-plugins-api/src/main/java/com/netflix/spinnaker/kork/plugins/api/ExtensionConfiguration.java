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

package com.netflix.spinnaker.kork.plugins.api;

import com.netflix.spinnaker.kork.annotations.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.annotation.Nonnull;

/**
 * Denotes that a class provides extension configuration. For example:
 *
 * <pre>{@code
 * &#064;ExtensionConfiguration("my-extension")
 * public class MyExtensionConfiguration {
 *   private String someProperty;
 * }
 * }</pre>
 */
@Beta
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Deprecated
public @interface ExtensionConfiguration {

  /**
   * The property value of the extension configuration. For example, if set to `netflix.orca-stage`
   * the corresponding config coordinates would be:
   *
   * <p>`spinnaker.extensibility.plugins.pluginId.extensions.netflix.orca-stage.config`
   *
   * @return
   */
  @Nonnull
  String value();
}
