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
package com.netflix.spinnaker.rosco.providers.util;

import com.netflix.frigga.ami.AppVersion;
import com.netflix.spinnaker.rosco.api.BakeRequest;
import com.netflix.spinnaker.rosco.api.BakeRequest.PackageType;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class PackageNameConverter {

  @Builder
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class OsPackageName {

    private String name;
    private String version;
    private String release;
    private String arch;

    public String qualifiedPackageName(PackageType packageType) {
      if (StringUtils.isNotEmpty(version)) {
        String releaseTag = StringUtils.isNotEmpty(release) ? "-" + release : "";
        return name
            + packageType.getUtil().getPackageManagerVersionSeparator()
            + version
            + releaseTag;
      } else {
        return null;
      }
    }
  }

  public static OsPackageName buildOsPackageName(PackageType packageType, String packageName) {
    return packageType.getUtil().parsePackageName(packageName);
  }

  public static List<OsPackageName> buildOsPackageNames(
      final PackageType packageType, List<String> packageNames) {
    return packageNames.stream()
        .map(packageName -> buildOsPackageName(packageType, packageName))
        .filter(osPackage -> StringUtils.isNotEmpty(osPackage.getName()))
        .collect(Collectors.toList());
  }

  public static String buildAppVersionStr(
      BakeRequest bakeRequest, OsPackageName osPackageName, PackageType packageType) {
    // As per source of AppVersion, these are valid appversion tags:
    //   subscriberha-1.0.0-h150
    //   subscriberha-1.0.0-h150.586499
    //   subscriberha-1.0.0-h150.586499/WE-WAPP-subscriberha/150
    String appVersion = osPackageName.getName();
    String version = osPackageName.getVersion();

    if (StringUtils.isNotEmpty(version)) {
      appVersion += packageType.getUtil().getVersionSeparator() + version;

      if (StringUtils.isNotEmpty(bakeRequest.getBuild_number())) {
        appVersion +=
            packageType.getUtil().getBuildNumberSeparator() + bakeRequest.getBuild_number();

        if (StringUtils.isNotEmpty(bakeRequest.getCommit_hash())) {
          appVersion +=
              packageType.getUtil().getCommitHashSeparator() + bakeRequest.getCommit_hash();
        }

        if (StringUtils.isNotEmpty(bakeRequest.getJob())) {
          // Travis job name and Jenkins job name with folder may contain slashes in the job name
          // that make AppVersion.parseName fail. Replace all slashes in the job name with hyphens.
          String job = bakeRequest.getJob().replaceAll("/", "-");
          appVersion += "/" + job + "/" + bakeRequest.getBuild_number();
        }
      }
    }

    if (AppVersion.parseName(appVersion) == null) {
      log.debug(
          String.format(
              "AppVersion.parseName() was unable to parse appVersionStr=%s executionId: %s)",
              appVersion, bakeRequest.getSpinnaker_execution_id()));
      return null;
    }

    return appVersion;
  }
}
