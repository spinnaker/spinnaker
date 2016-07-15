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

package com.netflix.spinnaker.fiat.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.fiat.model.resources.Application;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class FileBasedApplicationProvider implements ApplicationProvider {

  private static String DEFAULT_WATCHED_FILE_EXTENSION = ".config";

  @Setter
  private String watchedFileExtension = DEFAULT_WATCHED_FILE_EXTENSION;

  private ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

  private Map<String /* app name */, Application> applications = new ConcurrentHashMap<>();

  private Thread watchThread;

  public FileBasedApplicationProvider(){}

  @VisibleForTesting
  FileBasedApplicationProvider(Map<String, Application> applications) {
    this.applications = applications;
  }

  @Override
  public Set<Application> getApplications(@NonNull Collection<String> groups) {
    return applications
        .values()
        .stream()
        .filter(application ->
                    application.getRequiredGroupMembership().isEmpty() ||
                        !Collections.disjoint(application.getRequiredGroupMembership(), groups))
        .collect(Collectors.toSet());
  }

  public FileBasedApplicationProvider watch(String watchedDirectory) {
    watchThread = new Thread(new ConfigDirectoryWatcher(watchedDirectory));
    watchThread.start();
    return this;
  }

  @PreDestroy
  public void close() {
    if (watchThread != null && watchThread.isAlive()) {
      watchThread.interrupt();
    }
  }

  @RequiredArgsConstructor
  private class ConfigDirectoryWatcher implements Runnable {

    private final String watchedDirectory;

    @Override
    public void run() {
      Path watchedDirPath = Paths.get(watchedDirectory);
      parseExistingConfigFiles(watchedDirPath);
      watchForChanges(watchedDirPath);
    }

    private void parseExistingConfigFiles(Path watchedDirPath) {
      try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(watchedDirPath)) {
        for (Path config : dirStream) {
          if (!isConfigFile(config.getFileName().toString())) {
            continue;
          }

          parseApplicationConfig(config)
              .ifPresent(app -> {
                applications.put(app.getName(), app);
                log.info("Adding configured application: " + app.getName());
              });
        }
      } catch (IOException ioe) {
        log.error("Error reading existing application configuration files", ioe);
      }
    }

    private void watchForChanges(Path watchedDirPath) {
      try (WatchService watchService = watchedDirPath.getFileSystem().newWatchService()) {
        watchedDirPath.register(watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE);
        log.info("Watching " + watchedDirectory + " for file changes");
        while (true) {
          try {
            WatchKey watchKey = watchService.take();

            for (WatchEvent event : watchKey.pollEvents()) {
              String filepath = event.context().toString();
              if (!isConfigFile(filepath)) {
                continue;
              }

              switch (event.kind().toString()) {
                case "ENTRY_CREATE":
                case "ENTRY_MODIFY":
                  log.info("File created or modified: " + event.context().toString());
                  val fullConfigPath = watchedDirPath.resolve((Path) event.context());
                  parseApplicationConfig(fullConfigPath)
                      .filter(newApp -> newApp != applications.get(newApp.getName()))
                      .ifPresent(newApp -> applications.put(newApp.getName(), newApp));
                  break;
                case "ENTRY_DELETE":
                  log.info("File deleted: " + event.context().toString());
                  String appName = StringUtils.removeEnd(filepath, watchedFileExtension);
                  applications.remove(appName);
                  break;
              }
            }

            watchKey.reset();
          } catch (InterruptedException interrupt) {
            log.info("Application configuration directory watching interrupted.");
            break;
          }
        }
      } catch (IOException ioe) {
        log.error("Exception during application watching", ioe);
      } finally {
        log.info("Finished watching " + watchedDirectory + " for file changes");
      }
    }

    private boolean isConfigFile(String filepath) {
      return !filepath.startsWith(".") && filepath.endsWith(watchedFileExtension);
    }

    private Optional<Application> parseApplicationConfig(Path config) {
      try {
        return Optional.of(objectMapper.readValue(config.toFile(), Application.class));
      } catch (IOException ioe) {
        log.warn("Can't create application from config file: " + config.toString(), ioe);
      }
      return Optional.empty();
    }
  }
}
