/*
 * Copyright 2021 Salesforce.com, Inc.
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

package com.netflix.spinnaker.orca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class TestUtils {

  public static String getResource(String name) {
    try {
      return Resources.toString(TestUtils.class.getResource(name), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static <T> T getResource(ObjectMapper objectMapper, String name, Class<T> valueType) {
    try {
      return objectMapper.readValue(TestUtils.class.getResourceAsStream(name), valueType);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static InputStream getResourceAsStream(String name) {
    return TestUtils.class.getResourceAsStream(name);
  }
}
