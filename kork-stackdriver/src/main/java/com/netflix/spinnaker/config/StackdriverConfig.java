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

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;


import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;


@Configuration
@EnableConfigurationProperties
@Import({MetricsController.class})
@ConditionalOnExpression("${spectator.stackdriver.enabled:false}")
class StackdriverConfig {

  @Value("${spectator.applicationName:${spring.application.name}}")
  private String applicationName;

  @Value("${spectator.stackdriver.credentialsPath:}")
  private String credentialsPath;

  @Value("${spectator.stackdriver.projectName:}")
  private String projectName;

  @Value("${spectator.stackdriver.uniqueMetricsPerApplication:true}")
  private boolean uniqueMetricsPerApplication;

  // If provided, takes precedence over the *Regex filters above.
  @Value("${spectator.webEndpoint.prototypeFilter.path:}")
  private String prototypeFilterPath;

  @Value("${spectator.stackdriver.period:60}")
  private int pushPeriodSecs;

  private StackdriverWriter stackdriver;

  @Configuration
  @ConfigurationProperties(prefix="stackdriver")
  static public class StackdriverConfigurationHints {
    /**
     * This class lets spring load from our YAML file into a Hint instance.
     */
    static public class MutableHint extends MetricDescriptorCache.CustomDescriptorHint {
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

  @Autowired
  StackdriverConfigurationHints stackdriverHints;

  @Autowired
  Registry registry;

  /**
   * Schedule a thread to flush our registry into stackdriver periodically.
   *
   * This configures our StackdriverWriter as well.
   */
  @Bean
  public StackdriverWriter defaultStackdriverWriter() throws IOException {
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
        .setUniqueMetricsPerApplication(uniqueMetricsPerApplication)
        .setCustomTypeNamespace("spinnaker")
        .setProjectName(projectName)
        .setApplicationName(applicationName)
        .setCredentialsPath(credentialsPath)
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

    Observable.timer(pushPeriodSecs, TimeUnit.SECONDS)
        .repeat()
        .subscribe(interval -> { stackdriver.writeRegistry(registry); });

    return stackdriver;
  }
};
