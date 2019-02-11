/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta;

import com.netflix.kayenta.atlas.config.AtlasConfiguration;
import com.netflix.kayenta.aws.config.AwsConfiguration;
import com.netflix.kayenta.canaryanalysis.config.StandaloneCanaryAnalysisModuleConfiguration;
import com.netflix.kayenta.config.KayentaConfiguration;
import com.netflix.kayenta.config.WebConfiguration;
import com.netflix.kayenta.configbin.config.ConfigBinConfiguration;
import com.netflix.kayenta.datadog.config.DatadogConfiguration;
import com.netflix.kayenta.gcs.config.GcsConfiguration;
import com.netflix.kayenta.google.config.GoogleConfiguration;
import com.netflix.kayenta.graphite.config.GraphiteConfiguration;
import com.netflix.kayenta.influxdb.config.InfluxDbConfiguration;
import com.netflix.kayenta.judge.config.NetflixJudgeConfiguration;
import com.netflix.kayenta.memory.config.MemoryConfiguration;
import com.netflix.kayenta.newrelic.config.NewRelicConfiguration;
import com.netflix.kayenta.prometheus.config.PrometheusConfiguration;
import com.netflix.kayenta.s3.config.S3Configuration;
import com.netflix.kayenta.signalfx.config.SignalFxConfiguration;
import com.netflix.kayenta.stackdriver.config.StackdriverConfiguration;
import com.netflix.kayenta.wavefront.config.WavefrontConfiguration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Import({
  AtlasConfiguration.class,
  AwsConfiguration.class,
  ConfigBinConfiguration.class,
  DatadogConfiguration.class,
  GcsConfiguration.class,
  GoogleConfiguration.class,
  GraphiteConfiguration.class,
  InfluxDbConfiguration.class,
  KayentaConfiguration.class,
  MemoryConfiguration.class,
  NewRelicConfiguration.class,
  PrometheusConfiguration.class,
  S3Configuration.class,
  SignalFxConfiguration.class,
  StackdriverConfiguration.class,
  StandaloneCanaryAnalysisModuleConfiguration.class,
  WavefrontConfiguration.class,
  WebConfiguration.class,
  NetflixJudgeConfiguration.class,
})
@ComponentScan({
  "com.netflix.spinnaker.config",
})
@EnableAutoConfiguration
@EnableAsync
@EnableScheduling
public class Main extends SpringBootServletInitializer {
  private static final Map<String, Object> DEFAULT_PROPS = buildDefaults();

  private static Map<String, Object> buildDefaults() {
    Map<String, String> defaults = new HashMap<>();
    defaults.put("netflix.environment", "test");
    defaults.put("netflix.account", "${netflix.environment}");
    defaults.put("netflix.stack", "test");
    defaults.put("spring.config.location", "${user.home}/.spinnaker/");
    defaults.put("spring.application.name", "kayenta");
    defaults.put("spring.config.name", "spinnaker,${spring.application.name}");
    defaults.put("spring.profiles.active", "${netflix.environment},local");
    defaults.put("spring.jackson.serialization.WRITE_DATES_AS_TIMESTAMPS", "false");
    defaults.put("spring.jackson.default-property-inclusion", "non_null");
    return Collections.unmodifiableMap(defaults);
  }

  public static void main(String... args) {
    new SpringApplicationBuilder().properties(DEFAULT_PROPS).sources(Main.class).run(args);
  }

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    return application.properties(DEFAULT_PROPS).sources(Main.class);
  }
}
