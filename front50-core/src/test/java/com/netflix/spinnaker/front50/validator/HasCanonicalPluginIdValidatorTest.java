/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.front50.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.netflix.spinnaker.front50.model.plugins.PluginInfo;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.validation.Errors;

public class HasCanonicalPluginIdValidatorTest {
  private final HasCanonicalPluginIdValidator validator = new HasCanonicalPluginIdValidator();

  @ParameterizedTest
  @MethodSource("pluginIdProvider")
  public void requiresCanonicalPluginId(final String id, final boolean hasErrors) {
    PluginInfo pluginInfo = new PluginInfo();
    pluginInfo.setId(id);
    Errors errors = new GenericValidationErrors(pluginInfo);

    validator.validate(pluginInfo, errors);

    assertEquals(hasErrors, errors.hasErrors());
  }

  private static Stream<Object[]> pluginIdProvider() {
    return Stream.of(
        new Object[] {"foo", true},
        new Object[] {"foo/bar", true},
        new Object[] {"foo.bar", false},
        new Object[] {".", true},
        new Object[] {".bar", true},
        new Object[] {"foo.", true});
  }
}
