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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary;

public class CommonCanaryCommandProperties {
  public static final String BUCKET =
      "The name of a storage bucket that your specified account has access to. If you "
          + "specify a globally unique bucket name that doesn't exist yet, Kayenta will create that bucket for you.";

  public static final String ROOT_FOLDER =
      "The root folder in the chosen bucket to place all of the canary service's persistent data in "
          + "(*Default*: `kayenta`).";
}
