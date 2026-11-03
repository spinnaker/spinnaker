/*
 * Copyright 2017 Schibsted ASA.
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

package com.netflix.spinnaker.rosco.jobs

class BakeRecipe {
  String name
  String version
  List<String> command

  /**
   * Environment variables to set (in addition to the job executor's own environment) when
   * running {@link #command}. Used e.g. by helmfile bakes to pass HELMFILE_DISABLE_HOOKS /
   * HELMFILE_DISABLE_INSECURE_FEATURES to the helmfile subprocess.
   */
  Map<String, String> env = [:]
}
