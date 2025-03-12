/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy;

import com.netflix.spinnaker.clouddriver.aws.deploy.description.ModifyServerGroupLaunchTemplateDescription;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;

public class ModifyServerGroupUtils {

  public static Set<String> getNonMetadataFieldsSetInReq(
      final ModifyServerGroupLaunchTemplateDescription reqDescription) {
    final ModifyServerGroupLaunchTemplateDescription descWithDefaults =
        new ModifyServerGroupLaunchTemplateDescription();
    final Set<String> nonMetadataFieldsSet = new HashSet<>();

    // get all field names for the description type
    final Set<String> allFieldNames =
        FieldUtils.getAllFieldsList(reqDescription.getClass()).stream()
            .map(Field::getName)
            .collect(Collectors.toSet());

    // get the fields set in the request
    allFieldNames.stream()
        .forEach(
            fieldName -> {
              // ignore Groovy object's special fields
              if (fieldName.contains("$") || fieldName.equals("metaClass")) {
                return;
              }
              Object defaultValue = descWithDefaults.getProperty(fieldName);
              Object requestedValue = reqDescription.getProperty(fieldName);
              boolean isMetadataField =
                  ModifyServerGroupLaunchTemplateDescription.getMetadataFieldNames()
                      .contains(fieldName);
              if (!Objects.equals(requestedValue, defaultValue) && !isMetadataField) {
                nonMetadataFieldsSet.add(fieldName);
              }
            });

    return nonMetadataFieldsSet;
  }
}
