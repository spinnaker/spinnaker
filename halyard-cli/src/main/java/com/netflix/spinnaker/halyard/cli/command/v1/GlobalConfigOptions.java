/*
 * Copyright 2017 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1;

import lombok.Data;

/**
 * This is the collection of global config-level flags to be interpreted by halyard.
 */
@Data
public class GlobalConfigOptions {
  private String deployment;

  private static GlobalConfigOptions globalConfigOptions = null;

  public static GlobalConfigOptions getGlobalConfigOptions() {
    if (globalConfigOptions == null) {
      globalConfigOptions = new GlobalConfigOptions();
    }

    return globalConfigOptions;
  }
}
