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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.google;

public class CommonCanaryGoogleCommandProperties {
  public static final String PROJECT_DESCRIPTION =
      "The Google Cloud Platform project the canary service will use to consume GCS and Stackdriver.";

  public static final String BUCKET_LOCATION =
      "This is only required if the bucket you specify doesn't exist yet. In that case, the "
          + "bucket will be created in that location. See https://cloud.google.com/storage/docs/managing-buckets#manage-class-location.";
}
