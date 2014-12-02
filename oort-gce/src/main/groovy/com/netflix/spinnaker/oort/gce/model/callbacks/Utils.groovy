/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.model.callbacks

import java.text.SimpleDateFormat

class Utils {
  public static final String APPLICATION_NAME = "Spinnaker"

  // TODO(duftler): This list should be configurable.
  public static final List<String> baseImageProjects = ["centos-cloud", "coreos-cloud", "debian-cloud", "google-containers",
                                                        "opensuse-cloud", "rhel-cloud", "suse-cloud", "ubuntu-os-cloud"]

  private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")

  static long getTimeFromTimestamp(String timestamp) {
    return simpleDateFormat.parse(timestamp).getTime()
  }

  static String getLocalName(String fullUrl) {
    int lastIndex = fullUrl.lastIndexOf('/')

    return lastIndex != -1 ? fullUrl.substring(lastIndex + 1) : fullUrl
  }
}
