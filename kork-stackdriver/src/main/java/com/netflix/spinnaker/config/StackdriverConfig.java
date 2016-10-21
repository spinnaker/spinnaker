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

package com.netflix.spinnaker.config;

import com.netflix.spectator.controllers.filter.PrototypeMeasurementFilter;
import com.netflix.spectator.controllers.MetricsController;
import com.netflix.spectator.stackdriver.ConfigParams;
import com.netflix.spectator.stackdriver.MetricDescriptorCache;
import com.netflix.spectator.stackdriver.StackdriverWriter;

import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.springframework.core.env.Environment;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;


@Configuration
@EnableConfigurationProperties({StackdriverConfig.SpectatorStackdriverConfigurationProperties.class, StackdriverConfig.StackdriverConfigurationHints.class})
@ConditionalOnProperty("spectator.stackdriver.enabled")
public class StackdriverConfig {

  @Bean
  @ConditionalOnProperty("spectator.webEndpoint.enabled")
  MetricsController metricsController(Registry registry) {
    return new MetricsController();
  }

  @ConfigurationProperties("spectator")
  public static class SpectatorStackdriverConfigurationProperties {
    public static class StackdriverProperties {
      private String credentialsPath = "";
      private String projectName = "";
      private boolean uniqueMetricsPerApplication = true;
      private int period = 60;

      public String getCredentialsPath() {
        return credentialsPath;
      }

      public void setCredentialsPath(String credentialsPath) {
        this.credentialsPath = credentialsPath;
      }

      public String getProjectName() {
        return projectName;
      }

      public void setProjectName(String projectName) {
        this.projectName = projectName;
      }

      public boolean isUniqueMetricsPerApplication() {
        return uniqueMetricsPerApplication;
      }

      public void setUniqueMetricsPerApplication(boolean uniqueMetricsPerApplication) {
        this.uniqueMetricsPerApplication = uniqueMetricsPerApplication;
      }

      public int getPeriod() {
        return period;
      }

      public void setPeriod(int period) {
        this.period = period;
      }
    }

    public static class WebEndpointProperties {
      public static class PrototypeFilterPath {
        private String path = "";

        public String getPath() {
          return path;
        }

        public void setPath(String path) {
          this.path = path;
        }
      }

      @NestedConfigurationProperty
      private PrototypeFilterPath prototypeFilter = new PrototypeFilterPath();

      public PrototypeFilterPath getPrototypeFilter() {
        return prototypeFilter;
      }

      public void setPrototypeFilter(PrototypeFilterPath prototypeFilter) {
        this.prototypeFilter = prototypeFilter;
      }
    }

    private String applicationName;

    @NestedConfigurationProperty
    private StackdriverProperties stackdriver = new StackdriverProperties();

    @NestedConfigurationProperty
    private WebEndpointProperties webEndpoint = new WebEndpointProperties();

    public String getApplicationName(String defaultValue) {
      if (applicationName == null || applicationName.isEmpty()) {
        return defaultValue;
      }
      return applicationName;
    }

    public void setApplicationName(String applicationName) {
      this.applicationName = applicationName;
    }

    public StackdriverProperties getStackdriver() {
      return stackdriver;
    }

    public void setStackdriver(StackdriverProperties stackdriver) {
      this.stackdriver = stackdriver;
    }

    public WebEndpointProperties getWebEndpoint() {
      return webEndpoint;
    }

    public void setWebEndpoint(WebEndpointProperties webEndpoint) {
      this.webEndpoint = webEndpoint;
    }
  }

  private StackdriverWriter stackdriver;

  @ConfigurationProperties("stackdriver")
  public static class StackdriverConfigurationHints {
    /**
     * This class lets spring load from our YAML file into a Hint instance.
     */
    public static class MutableHint extends MetricDescriptorCache.CustomDescriptorHint {
        public void setLabels(List<String> labels) {
            this.labels = labels;
        }
        public void setRedacted(List<String> labels) {
            this.redacted = labels;
        }
        public void setName(String name) {
            this.name = name;
        }
    };

    public List<MutableHint> hints;
    public void setHints(List<MutableHint> hints) { this.hints = hints; }
    public List<MutableHint> getHints() { return hints; }
  };

  /**
   * Schedule a thread to flush our registry into stackdriver periodically.
   *
   * This configures our StackdriverWriter as well.
   */
  @Bean
  public StackdriverWriter defaultStackdriverWriter(Environment environment,
                                                    Registry registry,
                                                    StackdriverConfigurationHints stackdriverHints,
                                                    SpectatorStackdriverConfigurationProperties spectatorStackdriverConfigurationProperties) throws IOException {
    Logger log = LoggerFactory.getLogger("StackdriverConfig");
    log.info("Creating StackdriverWriter.");
    Predicate<Measurement> filterNotSpring = new Predicate<Measurement>() {
      public boolean test(Measurement measurement) {
        // Dont store measurements that dont have tags.
        // These are from spring; those of interest were replicated in spectator.
        if (measurement.id().tags().iterator().hasNext()) {
          return true;
        }

        return false;
      }
    };
    Predicate<Measurement> measurementFilter;

    final String prototypeFilterPath = spectatorStackdriverConfigurationProperties.getWebEndpoint().getPrototypeFilter().getPath();

    if (!prototypeFilterPath.isEmpty()) {
      log.error("Ignoring prototypeFilterPath because it is not yet supported.");
      measurementFilter = null;
      log.info("Configuring stackdriver filter from {}", prototypeFilterPath);
      measurementFilter = PrototypeMeasurementFilter.loadFromPath(
           prototypeFilterPath).and(filterNotSpring);
    } else {
      measurementFilter = filterNotSpring;
    }

    ConfigParams params = new ConfigParams.Builder()
        .setCounterStartTime(new Date().getTime())
        .setUniqueMetricsPerApplication(spectatorStackdriverConfigurationProperties.getStackdriver().isUniqueMetricsPerApplication())
        .setCustomTypeNamespace("spinnaker")
        .setProjectName(spectatorStackdriverConfigurationProperties.getStackdriver().getProjectName())
        .setApplicationName(spectatorStackdriverConfigurationProperties.getApplicationName(environment.getProperty("spring.application.name")))
        .setCredentialsPath(spectatorStackdriverConfigurationProperties.getStackdriver().getCredentialsPath())
        .setMeasurementFilter(measurementFilter)
        .build();

    stackdriver = new StackdriverWriter(params);

    if (stackdriverHints != null && stackdriverHints.hints != null) {
        log.info("Adding {} custom descriptor hints",
                 stackdriverHints.hints.size());
        stackdriver.getDescriptorCache()
            .addCustomDescriptorHints(stackdriverHints.hints);
    } else {
        log.info("No custom descriptor hints");
    }
    Scheduler scheduler = Schedulers.from(Executors.newFixedThreadPool(1));

    Observable.timer(spectatorStackdriverConfigurationProperties.getStackdriver().getPeriod(), TimeUnit.SECONDS)
        .repeat()
        .subscribe(interval -> { stackdriver.writeRegistry(registry); });

    return stackdriver;
  }
};
