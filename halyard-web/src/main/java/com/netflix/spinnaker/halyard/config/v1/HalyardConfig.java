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

import com.netflix.spinnaker.halyard.controllers.v1.UnknownPropertyException;
import com.netflix.spinnaker.halyard.model.v1.Halconfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.ConstructorException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/*
 * Since we aren't relying on SpringBoot to configure Halyard's ~/.hal/config, we instead use this class as a utility
 * method to read ~/.hal/config's contents.
 */
@Component
public class HalyardConfig {
  @Bean
  String halconfigPath(@Value("${halconfig.filesystem.halconfig:~/.hal/config}") String path) {
    path = path.replaceFirst("^~", System.getProperty("user.home"));
    return path;
  }

  @Bean
  String halconfigVersion(@Value("${Implementation-Version:unknown}") String version) {
    return version;
  }

  @Autowired
  String halconfigPath;

  @Autowired
  String halconfigVersion;

  public Halconfig getConfig() {
    Yaml yaml = new Yaml(new Constructor(Halconfig.class));
    Halconfig res = null;
    try {
      InputStream is = new FileInputStream(new File(halconfigPath));
      res = (Halconfig) yaml.load(is);
    } catch (FileNotFoundException e) {
      res = new Halconfig();
      res.halyardVersion = halconfigVersion;
    } catch (ConstructorException e) {
      throw new UnknownPropertyException(e.getContextMark());
    }
    return res;
  }
}
