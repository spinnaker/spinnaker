/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.provider.agent;

import com.google.common.base.Splitter;
import com.netflix.frigga.ami.AppVersion;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public class ImageDescriptorParser {
  private static final Splitter.MapSplitter IMAGE_DESCRIPTION_SPLITTER =
      Splitter.on(',').withKeyValueSeparator(": ");

  public static Map<String, Object> createBuildInfo(@Nullable String imageDescription) {
    if (imageDescription == null) {
      return Collections.emptyMap();
    }
    Map<String, String> tags;
    try {
      tags = IMAGE_DESCRIPTION_SPLITTER.split(imageDescription);
    } catch (IllegalArgumentException e) {
      return Collections.emptyMap();
    }
    if (!tags.containsKey("appversion")) {
      return Collections.emptyMap();
    }
    AppVersion appversion = AppVersion.parseName(tags.get("appversion"));
    if (appversion == null) {
      return Collections.emptyMap();
    }
    Map<String, Object> buildInfo = new HashMap<>();
    buildInfo.put("package_name", appversion.getPackageName());
    buildInfo.put("version", appversion.getVersion());
    buildInfo.put("commit", appversion.getCommit());
    if (appversion.getBuildJobName() != null) {
      Map<String, String> jenkinsInfo = new HashMap<>();
      jenkinsInfo.put("name", appversion.getBuildJobName());
      jenkinsInfo.put("number", appversion.getBuildNumber());
      if (tags.containsKey("build_host")) {
        jenkinsInfo.put("host", tags.get("build_host"));
      }
      buildInfo.put("jenkins", jenkinsInfo);
    }
    if (tags.containsKey("build_info_url")) {
      buildInfo.put("buildInfoUrl", tags.get("build_info_url"));
    }
    return buildInfo;
  }
}
