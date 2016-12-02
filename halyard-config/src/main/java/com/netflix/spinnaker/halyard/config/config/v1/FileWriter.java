/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.config.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.Map;

@Component
public class YamlWriter {
  @Autowired
  Yaml yamlParser;

  @Autowired
  ObjectMapper objectMapper;

  public void write(Object node, String path) throws IOException {
    write(node, path, false);
  }

  public void write(Object node, String path, boolean append) throws IOException {
    Writer writer = null;
    try {
      writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path, append), "utf-8"));
      writer.write(yamlParser.dump(objectMapper.convertValue(node, Map.class)));
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }
}
