/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.config;

import com.netflix.spinnaker.kork.annotations.Alpha;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * ModifiableFilePropertySources maintains a list of files that are file property sources that can
 * be watched and refreshed.
 *
 * <p>When a file is changed or added, it will be used as a {@link PropertySource}, the refresh
 * scope will be refreshed, and the application notified.
 */
@Slf4j
@Alpha
public class ModifiableFilePropertySources
    implements BeanPostProcessor, Ordered, Runnable, ApplicationListener<ApplicationReadyEvent> {
  private final List<DynamicFilePropertySource> dynamicFilePropertySources;
  private ConfigurableApplicationContext applicationContext;
  private RefreshScope refreshScope;

  public ModifiableFilePropertySources(
      ConfigurableApplicationContext applicationContext,
      RefreshScope refreshScope,
      List<String> dynamicConfigFiles) {
    this.applicationContext = applicationContext;
    this.refreshScope = refreshScope;
    this.dynamicFilePropertySources =
        dynamicConfigFiles.stream()
            .map(f -> new DynamicFilePropertySource(f))
            .collect(Collectors.toList());
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    Thread t = new Thread(this, "dynamicConfig");
    t.setDaemon(true);
    t.start();
  }

  @PostConstruct
  public void start() {
    dynamicFilePropertySources.stream().forEach(DynamicFilePropertySource::install);
  }

  private Optional<ModifiableFilePropertySources.DynamicFilePropertySource>
      getDynamicFilePropertySourceFromEvent(Watchable directory, Object context) {
    if (directory instanceof Path) {
      String path = ((Path) directory).resolve(context.toString()).toAbsolutePath().toString();
      return dynamicFilePropertySources.stream()
          .filter(f -> f.getAbsFilePath().equals(path))
          .findFirst();
    }
    return Optional.empty();
  }

  private Set<File> getPropertySourceDirectories() {
    Map<File, List<DynamicFilePropertySource>> directoryMap =
        dynamicFilePropertySources.stream()
            .collect(
                Collectors.groupingBy(f -> f.getFileSystemResource().getFile().getParentFile()));
    return directoryMap.keySet();
  }

  public void run() {
    try {
      Set<File> directoriesToWatch = getPropertySourceDirectories();

      WatchService watchService = FileSystems.getDefault().newWatchService();
      for (File directory : directoriesToWatch) {
        Path path = Paths.get(directory.getAbsolutePath());
        path.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY);
      }

      WatchKey key;
      while ((key = watchService.take()) != null) {
        List<WatchEvent<?>> events = key.pollEvents();
        try {
          boolean notify = false;
          for (WatchEvent event : events) {
            Optional<DynamicFilePropertySource> source =
                getDynamicFilePropertySourceFromEvent(key.watchable(), event.context());
            if (source.isPresent()) {
              log.info("Detected changes to {}", source.get().absFilePath);
              if (event.kind().equals(StandardWatchEventKinds.ENTRY_CREATE)
                  || event.kind().equals(StandardWatchEventKinds.ENTRY_MODIFY)
                  || event.kind().equals(StandardWatchEventKinds.ENTRY_DELETE)) {
                source.get().sync();
                notify = true;
              }
            }
          }
          if (notify) {
            refreshScope.refreshAll();
            applicationContext.publishEvent(new RefreshScopeRefreshedEvent());
          }
        } catch (Exception e) {
          log.error("Error refreshing dynamic config", e);
        } finally {
          key.reset();
        }
      }

    } catch (IOException | InterruptedException e) {
      log.error("Unable to watch dynamic config files", e);
    }
  }

  @Override
  public int getOrder() {
    return HIGHEST_PRECEDENCE + 7;
  }

  /**
   * Virtual property source that wraps an underlying property source backed by a file that can
   * change. Virtual is added so property source wrappers (like {@link
   * com.netflix.spinnaker.kork.secrets.SecretBeanPostProcessor} and {@link
   * com.netflix.spinnaker.kork.configserver.CloudConfigAwarePropertySource}) can still function
   * after a reload.
   */
  class DynamicFilePropertySource {
    @Getter private FileSystemResource fileSystemResource;
    @Getter private String absFilePath;
    private YamlPropertySourceLoader yamlPropertySourceLoader = new YamlPropertySourceLoader();
    private PropertySource dynamicPropertySource;
    private List<PropertySource<?>> propertySources = new ArrayList<>();

    public DynamicFilePropertySource(String filename) {
      absFilePath = Paths.get(filename).toAbsolutePath().toString();
      fileSystemResource = new FileSystemResource(filename);
    }

    public void install() {
      sync();
      dynamicPropertySource =
          new EnumerablePropertySource(absFilePath) {
            @Override
            public String[] getPropertyNames() {
              return propertySources.stream()
                  .filter(s -> s instanceof EnumerablePropertySource)
                  .map(s -> ((EnumerablePropertySource) s).getPropertyNames())
                  .reduce(new String[0], ArrayUtils::addAll);
            }

            @Override
            public Object getProperty(String name) {
              return propertySources.stream()
                  .map(s -> s.getProperty(name))
                  .filter(Objects::nonNull)
                  .findFirst()
                  .orElse(null);
            }
          };
      applicationContext.getEnvironment().getPropertySources().addFirst(dynamicPropertySource);
    }

    public void sync() {
      try {
        if (fileSystemResource.getFile().exists()) {
          propertySources =
              yamlPropertySourceLoader.load("dynamic:" + absFilePath, fileSystemResource);
        } else {
          propertySources = new ArrayList<>();
        }
      } catch (IOException e) {
        log.warn("Unable to load properties from " + absFilePath, e);
      }
    }
  }
}
