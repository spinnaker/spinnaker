/*
 * Copyright 2019 Google, Inc.
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
package com.netflix.spinnaker.rosco.providers.util.packagespecific;

import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter.OsPackageName;
import com.netflix.spinnaker.rosco.providers.util.PackageUtil;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class DebPackageUtil implements PackageUtil {

  @Override
  public String getPackageType() {
    return "deb";
  }

  @Override
  public String getPackageManagerVersionSeparator() {
    return "=";
  }

  @Override
  public String getVersionSeparator() {
    return "-";
  }

  @Override
  public String getBuildNumberSeparator() {
    return "-h";
  }

  @Override
  public String getCommitHashSeparator() {
    return ".";
  }

  @Override
  public OsPackageName parsePackageName(String fullyQualifiedPackageName) {
    // Naming-convention for debs is name_version-release_arch.
    // For example: nflx-djangobase-enhanced_0.1-h12.170cdbd_all

    if (StringUtils.isEmpty(fullyQualifiedPackageName)) {
      return new OsPackageName();
    }

    String name = fullyQualifiedPackageName;
    String version = null;
    String release = null;
    String arch = null;

    List<String> parts = Arrays.asList(fullyQualifiedPackageName.split("_"));

    if (parts.size() > 1) {
      List<String> versionReleaseParts = Arrays.asList(parts.get(1).split("-"));

      version = versionReleaseParts.get(0);
      name = parts.get(0);

      if (versionReleaseParts.size() > 1) {
        release = versionReleaseParts.get(1);
      }

      if (parts.size() > 2) {
        arch = parts.get(2);
      }

    }

    return OsPackageName.builder().name(name).version(version).release(release).arch(arch).build();
  }

}
