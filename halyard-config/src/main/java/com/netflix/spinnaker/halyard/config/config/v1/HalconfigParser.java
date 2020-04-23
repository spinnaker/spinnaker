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

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.FATAL;

import com.netflix.spinnaker.halyard.config.error.v1.ParseConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.AtomicFileWriter;
import com.netflix.spinnaker.halyard.core.GlobalApplicationOptions;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.parser.ParserException;
import org.yaml.snakeyaml.scanner.ScannerException;

/**
 * A parser for all Config read by Halyard at runtime.
 *
 * @see Halconfig
 *     <p>Since we aren't relying on SpringBoot to configure Halyard's ~/.hal/config, we instead use
 *     this class as a utility method to read ~/.hal/config's contents.
 */
@Slf4j
@Component
public class HalconfigParser {
  @Autowired StrictObjectMapper objectMapper;

  @Autowired HalconfigDirectoryStructure halconfigDirectoryStructure;

  @Autowired ApplicationContext applicationContext;

  private boolean useBackup = false;

  private Yaml getYamlParser() {
    return applicationContext.getBean(Yaml.class);
  }

  /**
   * Parse Halyard's config.
   *
   * @param is is the input stream to read from.
   * @return the fully parsed halconfig.
   * @see Halconfig
   */
  Halconfig parseHalconfig(InputStream is) throws IllegalArgumentException {
    Object obj = getYamlParser().load(is);
    return objectMapper.convertValue(obj, Halconfig.class);
  }

  /**
   * Parse Halyard's config for inmemory usage. HalConfigs parsed with this function will NOT be
   * written to disk for persistence.
   *
   * @param is is the input stream to read from.
   * @return the fully parsed halconfig.
   * @see Halconfig
   */
  public Halconfig setInmemoryHalConfig(ByteArrayInputStream is) throws IllegalArgumentException {
    Halconfig halconfig = parseHalconfig(is);

    DaemonTaskHandler.setContext(halconfig);

    return halconfig;
  }

  private InputStream getHalconfigStream() throws FileNotFoundException {
    String baseDirectory =
        useBackup
            ? halconfigDirectoryStructure.getBackupConfigPath().toString()
            : halconfigDirectoryStructure.getHalconfigPath();
    return new FileInputStream(new File(baseDirectory));
  }

  /**
   * Returns the current halconfig stored at the halconfigPath.
   *
   * @return the fully parsed halconfig.
   * @see Halconfig
   */
  public Halconfig getHalconfig() {
    Halconfig local = (Halconfig) DaemonTaskHandler.getContext();

    if (local == null) {
      try {
        InputStream is = getHalconfigStream();
        local = parseHalconfig(is);
      } catch (FileNotFoundException ignored) {
        // leave res as `null`
      } catch (ParserException e) {
        throw new ParseConfigException(e);
      } catch (ScannerException e) {
        throw new ParseConfigException(e);
      } catch (IllegalArgumentException e) {
        throw new ParseConfigException(e);
      }
    }

    local = transformHalconfig(local);
    DaemonTaskHandler.setContext(local);

    return local;
  }

  private Halconfig transformHalconfig(Halconfig input) {
    if (input == null) {
      log.info("No halconfig found - generating a new one...");
      input = new Halconfig();
    }

    input.parentify();
    input.setPath(halconfigDirectoryStructure.getHalconfigPath());

    return input;
  }

  /** Undoes changes to the staged in-memory halconfig. */
  public void undoChanges() {
    DaemonTaskHandler.setContext(null);
  }

  /** Write your halconfig object to the halconfigPath. */
  public void saveConfig() {
    saveConfigTo(Paths.get(halconfigDirectoryStructure.getHalconfigPath()));
  }

  /** Deletes all files in the staging directory that are not referenced in the hal config. */
  public void cleanLocalFiles(Path stagingDirectoryPath) {
    if (!GlobalApplicationOptions.getInstance().isUseRemoteDaemon()) {
      return;
    }
    Halconfig halconfig = getHalconfig();
    Set<String> referencedFiles = new HashSet<>();
    Consumer<Node> fileFinder =
        n ->
            referencedFiles.addAll(
                n.localFiles().stream()
                    .map(
                        f -> {
                          try {
                            f.setAccessible(true);
                            return (String) f.get(n);
                          } catch (IllegalAccessException e) {
                            throw new RuntimeException(
                                "Failed to clean staging directory: " + e.getMessage(), e);
                          } finally {
                            f.setAccessible(false);
                          }
                        })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
    halconfig.recursiveConsume(fileFinder);

    Set<String> existingStagingFiles =
        FileUtils.listFiles(
                stagingDirectoryPath.toFile(), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE)
            .stream()
            .map(f -> f.getAbsolutePath())
            .collect(Collectors.toSet());

    existingStagingFiles.removeAll(referencedFiles);

    try {
      for (String f : existingStagingFiles) {
        FileUtils.forceDelete(new File(f));
      }
    } catch (IOException e) {
      throw new HalException(FATAL, "Failed to clean staging directory: " + e.getMessage(), e);
    }
  }

  public void backupConfig() {
    // It's possible we are asked to backup the halconfig without having loaded it first.
    boolean backup = useBackup;
    useBackup = false;
    getHalconfig();
    useBackup = backup;
    saveConfigTo(halconfigDirectoryStructure.getBackupConfigPath());
  }

  public void switchToBackupConfig() {
    DaemonTaskHandler.setContext(null);
    useBackup = true;
  }

  public void switchToPrimaryConfig() {
    DaemonTaskHandler.setContext(null);
    useBackup = false;
  }

  private void saveConfigTo(Path path) {
    Halconfig local = (Halconfig) DaemonTaskHandler.getContext();
    if (local == null) {
      throw new HalException(
          new ConfigProblemBuilder(
                  Severity.WARNING, "No halconfig changes have been made, nothing to write")
              .build());
    }

    AtomicFileWriter writer = null;
    try {
      writer = new AtomicFileWriter(path);
      writer.write(getYamlParser().dump(objectMapper.convertValue(local, Map.class)));
      writer.commit();
    } catch (IOException e) {
      throw new HalException(
          Severity.FATAL,
          "Failure writing your halconfig to path \""
              + halconfigDirectoryStructure.getHalconfigPath()
              + "\": "
              + e.getMessage(),
          e);
    } finally {
      DaemonTaskHandler.setContext(null);
      if (writer != null) {
        writer.close();
      }
    }
  }
}
