/*
 * Copyright 2018 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.core.resource.v1;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class StringReplaceTemplatedResource extends TemplatedResource {
  public static Logger log = LoggerFactory.getLogger(StringReplaceTemplatedResource.class);

  protected String formatKey(String key) {
    return "{%" + key + "%}";
  }

  @Override
  public String toString() {
    String contents = getContents();
    for (Map.Entry<String, Object> binding : bindings.entrySet()) {
      Object value = binding.getValue();
      contents =
          contents.replace(formatKey(binding.getKey()), value != null ? value.toString() : "");
    }
    if (contents.matches("(?s).*\\" + formatKey(".*") + ".*")) {
      log.warn(
          "Found part of template that still contains a format key, likely a missing template value for a key, template: "
              + contents);
    }
    return contents;
  }
}
