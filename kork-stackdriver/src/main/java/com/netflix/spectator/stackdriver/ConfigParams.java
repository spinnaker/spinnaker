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

package com.netflix.spectator.stackdriver;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.MonitoringScopes;
import com.netflix.spectator.api.Measurement;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Factory to instantiate StackdriverWriter instance. */
public class ConfigParams {
  /** Derived if not explicitly set. */
  protected Monitoring monitoring;

  /** Required. The stackdriver project to store the data under. */
  protected String projectName;

  /** Required. */
  protected String customTypeNamespace;

  /** Required. */
  protected String applicationName;

  /** Derived if not explicitly set. */
  protected String instanceId;

  /** Overridable for testing only. */
  protected Function<String, String> determineProjectName =
      defaultName -> new MonitoredResourceBuilder().determineProjectName(defaultName);

  /** Optional. */
  protected Predicate<Measurement> measurementFilter;

  /** Derived if not set. */
  protected MetricDescriptorCache descriptorCache;

  /** Optional. */
  protected long counterStartTime;

  /** Builds an instance of ConfigParams. */
  public static class Builder extends ConfigParams {
    private String credentialsPath;

    private String validateString(String value, String purpose) {
      if (value == null || value.isEmpty()) {
        throw new IllegalStateException("The " + purpose + " has not been specified.");
      }
      return value;
    }

    /**
     * Ensure the configuration parameters are complete and valid.
     *
     * <p>If some required parameters are not yet explicitly set then this may initialize them.
     */
    public ConfigParams build() {
      ConfigParams result = new ConfigParams();

      String actualProjectName = determineProjectName.apply(projectName);
      result.projectName = validateString(actualProjectName, "stackdriver projectName");
      result.applicationName = validateString(applicationName, "applicationName");
      result.customTypeNamespace =
          validateString(customTypeNamespace, "stackdriver customTypeNamespace");

      result.counterStartTime = counterStartTime;
      result.measurementFilter = measurementFilter;

      result.instanceId = instanceId;
      result.monitoring = monitoring;
      result.descriptorCache = descriptorCache;

      if (result.instanceId == null || result.instanceId.isEmpty()) {
        UUID uuid = UUID.randomUUID();
        byte[] uuidBytes = new byte[16];
        addLong(uuidBytes, 0, uuid.getLeastSignificantBits());
        addLong(uuidBytes, 8, uuid.getMostSignificantBits());
        result.instanceId = java.util.Base64.getEncoder().encodeToString(uuidBytes);
      }

      if (result.monitoring == null) {
        try {
          HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
          JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
          GoogleCredential credential = loadCredential(transport, jsonFactory, credentialsPath);
          String version = getClass().getPackage().getImplementationVersion();
          if (version == null) {
            version = "Unknown";
          }

          result.monitoring =
              new Monitoring.Builder(transport, jsonFactory, credential)
                  .setApplicationName("Spinnaker/" + version)
                  .build();
        } catch (IOException | java.security.GeneralSecurityException e) {
          final Logger log = LoggerFactory.getLogger("StackdriverWriter");
          log.error("Caught exception initializing client: " + e);
          throw new IllegalStateException(e);
        }
      }

      if (result.descriptorCache == null) {
        result.descriptorCache = new MetricDescriptorCache(result);
      }
      return result;
    }

    /**
     * Overrides the function for determining the actual project name
     *
     * <p>This is for testing purposes only.
     */
    public Builder setDetermineProjectName(Function<String, String> fn) {
      determineProjectName = fn;
      return this;
    }

    /**
     * The Stackdriver namespace containing Custom Metric Descriptor types.
     *
     * <p>This is intended to group a family of metric types together for a particular system. (e.g.
     * "spinnaker")
     */
    public Builder setCustomTypeNamespace(String name) {
      customTypeNamespace = name;
      return this;
    }

    /**
     * Sets the Google project name for Stackdriver to manage metrics under.
     *
     * <p>This is a Google Cloud Platform project owned by the user deploying the system using
     * Spectator and used by Stackdriver to catalog our data (e.g. "my-unique-project").
     */
    public Builder setProjectName(String name) {
      projectName = name;
      return this;
    }

    /**
     * The path to specific Google Credentials create the client stub with.
     *
     * <p>This is not needed if injecting a specific Monitoring client stub.
     */
    public Builder setCredentialsPath(String path) {
      credentialsPath = path;
      return this;
    }

    /**
     * Sets the application name that we are associating these metrics with.
     *
     * <p>It distinguishes similar metrics within a system that are coming from different services.
     * (e.g. "clouddriver")
     */
    public Builder setApplicationName(String name) {
      applicationName = name;
      return this;
    }

    /**
     * Sets the instance id that we are associating these metrics with.
     *
     * <p>This will be the INSTANCE_LABEL value for the metrics we store.
     */
    public Builder setInstanceId(String id) {
      instanceId = id;
      return this;
    }

    /** Sets the Spectator MeasurementFilter determining which metrics to store in Stackdriver. */
    public Builder setMeasurementFilter(Predicate<Measurement> filter) {
      measurementFilter = filter;
      return this;
    }

    /**
     * Overrides the normal Stackdriver Monitoring client to use when interacting with Stackdriver.
     */
    public Builder setStackdriverStub(Monitoring stub) {
      monitoring = stub;
      return this;
    }

    /** Overrides the normal MetricDescriptorCache for the Stackdriver server. */
    public Builder setDescriptorCache(MetricDescriptorCache cache) {
      descriptorCache = cache;
      return this;
    }

    /** Specifies the starting time interval for CUMULATIVE Stackdriver metric descriptor types. */
    public Builder setCounterStartTime(long millis) {
      counterStartTime = millis;
      return this;
    }

    /** Helper function to encode a value into buffer when constructing a UUID. */
    private void addLong(byte[] buffer, int offset, long value) {
      for (int i = 0; i < 8; ++i) {
        buffer[i + offset] = (byte) (value >> (8 * i) & 0xff);
      }
    }

    /** Helper function for the validator that reads our credentials for talking to Stackdriver. */
    private static GoogleCredential loadCredential(
        HttpTransport transport, JsonFactory factory, String credentialsPath) throws IOException {
      final Logger log = LoggerFactory.getLogger("StackdriverWriter");

      GoogleCredential credential;
      if (credentialsPath != null && !credentialsPath.isEmpty()) {
        FileInputStream stream = new FileInputStream(credentialsPath);
        try {
          credential =
              GoogleCredential.fromStream(stream, transport, factory)
                  .createScoped(Collections.singleton(MonitoringScopes.MONITORING));
          log.info("Loaded credentials from from {}", credentialsPath);
        } finally {
          stream.close();
        }
      } else {
        log.info(
            "spectator.stackdriver.monitoring.enabled without"
                + " spectator.stackdriver.credentialsPath. "
                + " Using default application credentials.");
        credential = GoogleCredential.getApplicationDefault();
      }
      return credential;
    }
  }
  ;

  /** The Stackdriver namespace containing Custom Metric Descriptor types. */
  public String getCustomTypeNamespace() {
    return customTypeNamespace;
  }

  /** The Google project name for Stackdriver to manage metrics under. */
  public String getProjectName() {
    return projectName;
  }

  /** The application name that we are associating these metrics with. */
  public String getApplicationName() {
    return applicationName;
  }

  /** The instance id that we are associating these metrics with. */
  public String getInstanceId() {
    return instanceId;
  }

  /** Determines which metrics to write into stackdriver. */
  public Predicate<Measurement> getMeasurementFilter() {
    return measurementFilter;
  }

  /** The Stackdriver Monitoring client stub. */
  public Monitoring getStackdriverStub() {
    return monitoring;
  }

  /** The MetricDescriptorCache. */
  public MetricDescriptorCache getDescriptorCache() {
    return descriptorCache;
  }

  /** The TimeInterval start time for CUMULATIVE metric descriptor types. */
  public long getCounterStartTime() {
    return counterStartTime;
  }
}
;
