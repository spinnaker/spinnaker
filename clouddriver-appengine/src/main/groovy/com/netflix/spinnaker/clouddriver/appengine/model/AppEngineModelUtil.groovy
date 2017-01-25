/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.model

import com.google.api.services.appengine.v1.model.Version
import com.google.common.annotations.VisibleForTesting

import java.text.SimpleDateFormat

class AppEngineModelUtil {
  private static final dateFormats = ["yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"]
    .collect { new SimpleDateFormat(it) }

  static Long translateTime(String time) {
    for (SimpleDateFormat dateFormat: dateFormats) {
      try {
        return dateFormat.parse(time).getTime()
      } catch (e) { }
    }

    null
  }

  static AppEngineScalingPolicy getScalingPolicy(Version version) {
    if (version.getAutomaticScaling()) {
      return new AppEngineScalingPolicy(version.getAutomaticScaling())
    } else if (version.getBasicScaling()) {
      return new AppEngineScalingPolicy(version.getBasicScaling())
    } else if (version.getManualScaling()) {
      return new AppEngineScalingPolicy(version.getManualScaling())
    } else {
      return new AppEngineScalingPolicy()
    }
  }

  static String getHttpUrl(String selfLink) {
    "http://${getUrl(selfLink, ".")}"
  }

  static String getHttpsUrl(String selfLink) {
    "https://${getUrl(selfLink, "-dot-")}"
  }

  private static final String baseUrl = ".appspot.com"

  /*
    Self link has form apps/myapp/services/myservice/versions/myversion
    HTTPS: myversion-dot-myservice-dot-myapp.appspot.com
    HTTP: myversion.myservice.myapp.appspot.com

    This should work for services and versions, and for
    the default service and its versions ("default" can be omitted from their URLs).
  */

  @VisibleForTesting
  private static String getUrl(String selfLink, String delimiter) {
    def parts = selfLink.split("/").reverse()
    def componentNames = []
    parts.eachWithIndex { String entry, int i ->
      if (i % 2 == 0 && entry != "default") {
        componentNames << entry
      }
    }

    return componentNames.join(delimiter) + baseUrl
  }
}
