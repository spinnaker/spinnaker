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
import com.netflix.spinnaker.halyard.config.errors.v1.config.ParseConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.problem.Problem;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
  Halconfig parseHalconfig(InputStream is) throws UnrecognizedPropertyException {
    try {
      Object obj = yamlParser.load(is);
      return objectMapper.convertValue(obj, Halconfig.class);
    } catch (IllegalArgumentException e) {
      throw (UnrecognizedPropertyException) e.getCause();
    }
  }

  public InputStream getHalconfigStream() throws FileNotFoundException {
    return new FileInputStream(new File(halconfigPath));
  }

  /**
   * Returns the current halconfig stored at the halconfigPath.
   *
   * @see Halconfig
   * @param reload if we should check the disk for the halconfig.
   * @return the fully parsed halconfig.
   */
  public Halconfig getHalconfig(boolean reload) {
    Halconfig res = null;

    if (!reload && halconfig != null) {
      res = halconfig;
    } else {
      try {
        InputStream is = getHalconfigStream();
        res = parseHalconfig(is);
      } catch (FileNotFoundException e) {
      } catch (UnrecognizedPropertyException e) {
        throw new ParseConfigException(e);
      } catch (ParserException e) {
        throw new ParseConfigException(e);
      } catch (ScannerException e) {
        throw new ParseConfigException(e);
      }
    }

    return transformHalconfig(res);
  }

  Halconfig transformHalconfig(Halconfig halconfig) {
    if (halconfig == null) {
      log.info("No halconfig found generating a new one...");
      halconfig = new Halconfig();
    }

    halconfig.parentify();
    halconfig.setPath(halconfigPath);

    halconfig = halconfig;

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

    AtomicFileWriter writer = null;
    try {
      writer = new AtomicFileWriter(halconfigPath);
      writer.write(yamlParser.dump(objectMapper.convertValue(halconfig, Map.class)));
      writer.commit();
    } catch (IOException e) {
      throw new HalconfigException(
          new ProblemBuilder(Problem.Severity.FATAL,
              "Failure writing your halconfig to path \"" + halconfigPath + "\"").build()
      );
    } finally {
      halconfig = null;
      if (writer != null) {
        writer.close();
      }
    }
  }
}
