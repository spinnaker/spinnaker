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
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.netflix.spinnaker.halyard.config.errors.v1.HalconfigException;
import com.netflix.spinnaker.halyard.config.errors.v1.config.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.errors.v1.config.ParseConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.parser.ParserException;
import org.yaml.snakeyaml.scanner.ScannerException;

import java.io.*;
import java.util.Map;

/**
 * A parser for all Config read by Halyard at runtime.
 *
 * @see Halconfig
 *
 * Since we aren't relying on SpringBoot to configure Halyard's ~/.hal/config, we instead use this class as a utility
 * method to read ~/.hal/config's contents.
 */
@Slf4j
@Component
public class HalconfigParser {
  /**
   * Path to where the halconfig file is located.
   *
   * @param path Defaults to ~/.hal/config.
   * @return The path with home (~) expanded.
   */
  @Bean
  String halconfigPath(@Value("${halconfig.filesystem.halconfig:~/.hal/config}") String path) {
    path = path.replaceFirst("^~", System.getProperty("user.home"));
    return path;
  }

  /**
   * Version of halyard.
   *
   * This is useful for implementing breaking version changes in Spinnaker that need to be migrated by some tool
   * (in this case Halyard).
   *
   * @param version is the version (seems like the i.d. function for Spring Boot).
   * @return the version of halyard.
   */
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

  static Halconfig halconfig;

  /**
   * Parse Halyard's config.
   *
   * @see Halconfig
   * @param is is the input stream to read from.
   * @return the fully parsed halconfig.
   */
  Halconfig parseConfig(InputStream is) throws UnrecognizedPropertyException {
    try {
      Object obj = yamlParser.load(is);
      return objectMapper.convertValue(obj, Halconfig.class);
    } catch (IllegalArgumentException e) {
      throw (UnrecognizedPropertyException) e.getCause();
    }
  }

  /**
   * Returns the current halconfig stored at the halconfigPath.
   *
   * @see HalconfigParser#halconfigPath(String)
   * @see Halconfig
   * @param reload if we should check the disk for the halconfig.
   * @return the fully parsed halconfig.
   */
  public Halconfig getConfig(boolean reload) {
    if (!reload && halconfig != null) {
      return halconfig;
    }

    Halconfig res = null;
    try {
      InputStream is = new FileInputStream(new File(halconfigPath));
      res = parseConfig(is);
    } catch (FileNotFoundException e) {
    } catch (UnrecognizedPropertyException e) {
      throw new ParseConfigException(e);
    } catch (ParserException e) {
      throw new ParseConfigException(e);
    } catch (ScannerException e) {
      throw new ParseConfigException(e);
    }

    if (res == null) {
      log.info("No halconfig found generating a new one...");
      res = new Halconfig();
    }

    res.parentify();
    res.setPath(halconfigPath);

    halconfig = res;

    return halconfig;
  }

  /**
   * Undoes changes to the staged in-memory halconfig.
   */
  public void undoChanges() {
    halconfig = null;
  }

  /**
   * Write your halconfig object to the halconfigPath.
   */
  public void saveConfig() {
    if (halconfig == null) {
      throw new HalconfigException(
          new ProblemBuilder(Problem.Severity.WARNING,
              "No halconfig changes have been made, nothing to write")
          .build()
      );
    }

    Writer writer = null;
    try {
      writer = new BufferedWriter(
          new OutputStreamWriter(new FileOutputStream(halconfigPath), "utf-8"));
      writer.write(yamlParser.dump(objectMapper.convertValue(halconfig, Map.class)));
    } catch (Exception e) {
      throw new HalconfigException(
          new ProblemBuilder(Problem.Severity.FATAL,
          "Failure to write your halconfig to path \"" + halconfigPath + "\": " + e.getMessage())
          .build()
      );
    } finally {
      halconfig = null;
      if (writer != null) {
        try {
          writer.close();
        } catch (IOException e) {
          throw new HalconfigException(
              new ProblemBuilder(Problem.Severity.FATAL,
              "Failure to close your halconfig to path \"" + halconfigPath + "\": " + e.getMessage())
              .build()
          );
        }
      }
    }
  }
}
