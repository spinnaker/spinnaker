/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rosco.providers.util

import com.netflix.spinnaker.rosco.api.BakeRequest

/**
 * Placeholder implementation of ImageNameFactory. Considers only package_name, a timestamp, and base_os.
 */
public class DefaultImageNameFactory implements ImageNameFactory {

  @Override
  String produceImageName(BakeRequest bakeRequest) {
    // TODO(duftler): This is a placeholder. Need to properly support naming conventions.
    def timestamp = System.currentTimeMillis()

    "$bakeRequest.package_name-x8664-$timestamp-$bakeRequest.base_os"
  }

}
