/*
 * Copyright 2022 Armory.
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

package com.netflix.spinnaker.rosco.providers.azure.util;

import com.netflix.spinnaker.rosco.api.BakeRequest;
import com.netflix.spinnaker.rosco.providers.util.ImageNameFactory;
import com.netflix.spinnaker.rosco.providers.util.PackageNameConverter;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class AzureImageNameFactory extends ImageNameFactory {

  @Override
  public String buildImageName(
      BakeRequest bakeRequest, List<PackageNameConverter.OsPackageName> osPackageNames) {
    String timestamp = String.valueOf(getClock().millis());

    Optional<PackageNameConverter.OsPackageName> firstPackage = osPackageNames.stream().findFirst();
    String baseImageName = firstPackage.map(PackageNameConverter.OsPackageName::getName).orElse("");

    String baseName =
        bakeRequest.getBase_name() != null ? bakeRequest.getBase_name() : baseImageName;
    String arch = firstPackage.map(PackageNameConverter.OsPackageName::getArch).orElse("all");

    String release =
        bakeRequest.getBase_label() != null ? bakeRequest.getBase_label().name() : timestamp;
    String os =
        bakeRequest.getBase_os() != null
            ? bakeRequest.getBase_os()
            : bakeRequest.getOs_type().name();

    List<String> nameParams = new java.util.ArrayList<>(List.of(baseName, arch, release, os));
    nameParams.removeIf(StringUtils::isBlank);
    return String.join("-", nameParams);
  }
}
