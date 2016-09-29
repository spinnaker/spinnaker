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

package com.netflix.spinnaker.halyard.config.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.netflix.spinnaker.halyard.model.v1.Halconfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/*
 * Since we aren't relying on SpringBoot to configure Halyard's ~/.hal/config, we instead use this class as a utility
 * method to read ~/.hal/config's contents.
 */
@Component
public class HalconfigParser {
  @Bean
  String halconfigPath(@Value("${halconfig.filesystem.halconfig:~/.hal/config}") String path) {
    path = path.replaceFirst("^~", System.getProperty("user.home"));
    return path;
  }

  @Bean
  String halyardVersion(@Value("${Implementation-Version:unknown}") String version) {
    return version;
  }

  @Autowired
  String halconfigPath;

  @Autowired
  String halyardVersion;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  Yaml yamlParser;

  Halconfig parseConfig(InputStream is) throws UnrecognizedPropertyException {
    try {
      Object obj = yamlParser.load(is);
      return objectMapper.convertValue(obj, Halconfig.class);
    } catch (IllegalArgumentException e) {
      throw (UnrecognizedPropertyException) e.getCause();
    }
  }

  public Halconfig getConfig() throws UnrecognizedPropertyException {
    Halconfig res = null;
    try {
      InputStream is = new FileInputStream(new File(halconfigPath));
      res = parseConfig(is);
    } catch (FileNotFoundException e) {
      res = new Halconfig().setHalyardVersion(halyardVersion);
    }
    return res;
  }
}
