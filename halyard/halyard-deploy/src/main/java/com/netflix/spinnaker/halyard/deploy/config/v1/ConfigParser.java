/*
 * Copyright 2017 Google, Inc.
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
 */

package com.netflix.spinnaker.halyard.deploy.config.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.core.AtomicFileWriter;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class ConfigParser {
  @Autowired private Yaml yamlParser;

  @Autowired private ObjectMapper objectMapper;

  public String yamlToString(Object yaml) {
    return yamlParser.dump(objectMapper.convertValue(yaml, Map.class));
  }

  public void atomicWrite(Path path, Object obj) {
    String contents = yamlToString(obj);
    AtomicFileWriter writer = null;
    try {
      writer = new AtomicFileWriter(path);
      writer.write(contents);
      writer.commit();
    } catch (IOException ioe) {
      ioe.printStackTrace();
      throw new HalException(
          new ProblemBuilder(
                  Problem.Severity.FATAL,
                  "Failed to write config for profile "
                      + path.toFile().getName()
                      + ": "
                      + ioe.getMessage())
              .build());
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }

  public <T> T read(Path path, Class<T> tClass) {
    try {
      InputStream is = new FileInputStream(path.toFile());
      Object obj = yamlParser.load(is);
      return objectMapper.convertValue(obj, tClass);
    } catch (IllegalArgumentException e) {
      throw new HalException(
          new ProblemBuilder(
                  Problem.Severity.FATAL,
                  "Failed to load " + tClass.getSimpleName() + " config: " + e.getMessage())
              .build());
    } catch (FileNotFoundException e) {
      throw new HalException(
          new ProblemBuilder(
                  Problem.Severity.FATAL,
                  "Failed to find " + tClass.getSimpleName() + " config: " + e.getMessage())
              .build());
    }
  }
}
