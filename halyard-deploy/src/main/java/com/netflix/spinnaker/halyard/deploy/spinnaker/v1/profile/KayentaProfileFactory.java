/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.canary.aws.AwsCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.aws.AwsCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.datadog.DatadogCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.datadog.DatadogCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.prometheus.PrometheusCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.prometheus.PrometheusCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService.Type;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KayentaProfileFactory extends SpringProfileFactory {

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.KAYENTA;
  }

  @Override
  protected void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    super.setProfile(profile, deploymentConfiguration, endpoints);
    profile.appendContents(profile.getBaseContents());

    Canary canary = deploymentConfiguration.getCanary();

    if (canary.isEnabled()) {
      List<String> files = new ArrayList<>(backupRequiredFiles(canary, deploymentConfiguration.getName()));
      KayentaConfigWrapper kayentaConfig = new KayentaConfigWrapper(endpoints.getServiceSettings(Type.KAYENTA), canary);
      profile.appendContents(yamlToString(kayentaConfig)).setRequiredFiles(files);
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  private static class KayentaConfigWrapper extends SpringProfileConfig {
    KayentaConfig kayenta;

    KayentaConfigWrapper(ServiceSettings kayentaSettings, Canary canary) {
      super(kayentaSettings);
      kayenta = new KayentaConfig(canary);
    }

    @Data
    static class KayentaConfig {
      GoogleConfig google;
      StackdriverConfig stackdriver;
      GcsConfig gcs;
      PrometheusConfig prometheus;
      DatadogConfig datadog;
      AwsConfig aws;
      S3Config s3;

      KayentaConfig(Canary canary) {
        for (AbstractCanaryServiceIntegration svc : canary.getServiceIntegrations()) {
          if (svc instanceof GoogleCanaryServiceIntegration) {
            GoogleCanaryServiceIntegration googleSvc = (GoogleCanaryServiceIntegration)svc;
            google = new GoogleConfig(googleSvc);
            stackdriver = new StackdriverConfig(googleSvc);
            gcs = new GcsConfig(googleSvc);
          } else if (svc instanceof PrometheusCanaryServiceIntegration) {
            PrometheusCanaryServiceIntegration prometheusSvc = (PrometheusCanaryServiceIntegration)svc;
            prometheus = new PrometheusConfig(prometheusSvc);
          } else if (svc instanceof DatadogCanaryServiceIntegration) {
            DatadogCanaryServiceIntegration datadogSvc = (DatadogCanaryServiceIntegration)svc;
            datadog = new DatadogConfig(datadogSvc);
          } else if (svc instanceof AwsCanaryServiceIntegration) {
            AwsCanaryServiceIntegration awsSvc = (AwsCanaryServiceIntegration)svc;
            aws = new AwsConfig(awsSvc);
            s3 = new S3Config(awsSvc);
          }
        }
      }

      @Data
      static class GoogleConfig {
        private boolean enabled;
        List<GoogleCanaryAccount> accounts;

        GoogleConfig(GoogleCanaryServiceIntegration googleSvc) {
          enabled = googleSvc.isEnabled();
          accounts = googleSvc.getAccounts();
        }
      }

      @Data
      static class StackdriverConfig {
        private boolean enabled;
        private Long metadataCachingIntervalMS;

        StackdriverConfig(GoogleCanaryServiceIntegration googleSvc) {
          enabled = googleSvc.isStackdriverEnabled();
          metadataCachingIntervalMS = googleSvc.getMetadataCachingIntervalMS();
        }
      }

      @Data
      static class GcsConfig {
        private boolean enabled;

        GcsConfig(GoogleCanaryServiceIntegration googleSvc) {
          enabled = googleSvc.isGcsEnabled();
        }
      }

      @Data
      static class PrometheusConfig {
        private boolean enabled;
        private Long metadataCachingIntervalMS;
        List<PrometheusCanaryAccount> accounts;

        PrometheusConfig(PrometheusCanaryServiceIntegration prometheusSvc) {
          enabled = prometheusSvc.isEnabled();
          metadataCachingIntervalMS = prometheusSvc.getMetadataCachingIntervalMS();
          accounts = prometheusSvc.getAccounts();
        }
      }

      @Data
      static class DatadogConfig {
        private boolean enabled;
        List<DatadogCanaryAccount> accounts;

        DatadogConfig(DatadogCanaryServiceIntegration datadogSvc) {
          enabled = datadogSvc.isEnabled();
          accounts = datadogSvc.getAccounts();
        }
      }

      @Data
      static class AwsConfig {
        private boolean enabled;
        List<AwsCanaryAccount> accounts;

        AwsConfig(AwsCanaryServiceIntegration awsSvc) {
          enabled = awsSvc.isEnabled();
          accounts = awsSvc.getAccounts();
        }
      }

      @Data
      static class S3Config {
        private boolean enabled;

        S3Config(AwsCanaryServiceIntegration awsSvc) {
          enabled = awsSvc.isS3Enabled();
        }
      }
    }
  }
}