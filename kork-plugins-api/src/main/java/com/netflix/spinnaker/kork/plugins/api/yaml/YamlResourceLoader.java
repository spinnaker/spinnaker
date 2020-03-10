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

package com.netflix.spinnaker.kork.plugins.api.yaml;

import com.netflix.spinnaker.kork.annotations.Beta;
import javax.annotation.Nonnull;

/** Util to load a YML resource and convert to a POJO . */
@Beta
public interface YamlResourceLoader {

  /**
   * Loads the YML resource and returns object
   *
   * @param resourceName YML file resource co-located with plugin source.
   * @param toValueType converts to this type.
   * @return Returns object of the specified type
   */
  public <T> T loadResource(@Nonnull String resourceName, @Nonnull Class<T> toValueType);
}
