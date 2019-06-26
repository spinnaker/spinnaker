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

public class RpmPackageUtil implements PackageUtil {
  @Override
  public String getPackageType() {
    return "rpm";
  }

  @Override
  public String getPackageManagerVersionSeparator() {
    return "-";
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
    // Naming-convention for rpms is name-version-release.arch.
    // For example: nflx-djangobase-enhanced-0.1-h12.170cdbd.all
    if (StringUtils.isEmpty(fullyQualifiedPackageName)) {
      return new OsPackageName();
    }

    String name = fullyQualifiedPackageName;
    String arch = null;
    String release = null;
    String version = null;

    List<String> nameParts = Arrays.asList(fullyQualifiedPackageName.split("\\."));
    int numberOfNameParts = nameParts.size();

    if (numberOfNameParts >= 2) {
      fullyQualifiedPackageName = String.join(".", nameParts.subList(0, numberOfNameParts - 1));
      arch = String.join("", nameParts.subList(numberOfNameParts - 1, numberOfNameParts));
    }

    List<String> parts = Arrays.asList(fullyQualifiedPackageName.split("-"));

    if (parts.size() >= 3) {
      name = String.join("-", parts.subList(0, parts.size() - 2));
      version = parts.get(parts.size() - 2);
      release = parts.get(parts.size() - 1);
    }

    return OsPackageName.builder().name(name).version(version).release(release).arch(arch).build();
  }
}
