/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.front50.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.netflix.spinnaker.front50.model.plugins.PluginInfo;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class HasValidRequiresFieldsValidatorTest {
  private HasValidRequiresFieldsValidator validator = new HasValidRequiresFieldsValidator();

  @ParameterizedTest(
      name = "requires release with valid requires field formatting {0} has errors {1}")
  @MethodSource("fieldsProvider")
  @DisplayName("Field validation test")
  public void testFieldsValidation(String requiresValue, boolean hasErrors) {
    PluginInfo pluginInfo = new PluginInfo();
    PluginInfo.Release release = new PluginInfo.Release();
    release.setRequires(requiresValue);
    pluginInfo.setReleases(List.of(release));
    GenericValidationErrors errors = new GenericValidationErrors(pluginInfo);

    validator.validate(pluginInfo, errors);
    System.out.println(errors);

    assertEquals(hasErrors, errors.hasErrors());
  }

  private static Stream<Object[]> fieldsProvider() {
    return Stream.of(
        new Object[] {"gate<=1.0.0,echo>=1.0.0", false},
        new Object[] {"gate<=1.0.0, echo>=1.0.0", false},
        new Object[] {"gate>=1.0.0", false},
        new Object[] {"gate<1.0.0", false},
        new Object[] {"hello-world=1.0.0", false},
        new Object[] {"gate=1.0.0", false},
        new Object[] {"gate=foo", true});
  }
}
