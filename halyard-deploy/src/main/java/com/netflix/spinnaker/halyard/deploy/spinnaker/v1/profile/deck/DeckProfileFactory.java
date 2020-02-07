/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.deck;

import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Features;
import com.netflix.spinnaker.halyard.config.model.v1.node.Notifications;
import com.netflix.spinnaker.halyard.config.model.v1.notifications.GithubStatusNotification;
import com.netflix.spinnaker.halyard.config.model.v1.notifications.SlackNotification;
import com.netflix.spinnaker.halyard.config.model.v1.notifications.TwilioNotification;
import com.netflix.spinnaker.halyard.config.model.v1.providers.appengine.AppengineProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.azure.AzureProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.cloudfoundry.CloudFoundryProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.ecs.EcsProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.halyard.config.model.v1.security.UiSecurity;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.config.services.v1.VersionsService;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.core.resource.v1.StringResource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.RegistryBackedProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DeckProfileFactory extends RegistryBackedProfileFactory {

  @Autowired AccountService accountService;

  @Autowired VersionsService versionsService;

  @Override
  public String commentPrefix() {
    return "// ";
  }

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.DECK;
  }

  @Override
  protected void setProfile(
      Profile profile,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints) {
    StringResource configTemplate = new StringResource(profile.getBaseContents());
    UiSecurity uiSecurity = deploymentConfiguration.getSecurity().getUiSecurity();
    profile.setUser(ApacheSettings.APACHE_USER);

    Features features = deploymentConfiguration.getFeatures();
    Notifications notifications = deploymentConfiguration.getNotifications();
    Map<String, Object> bindings = new HashMap<>();
    String version = deploymentConfiguration.getVersion();

    // Configure global settings
    bindings.put("gate.baseUrl", endpoints.getServiceSettings(Type.GATE).getBaseUrl());
    bindings.put("timezone", deploymentConfiguration.getTimezone());
    bindings.put("version", deploymentConfiguration.getVersion());

    Versions versions = versionsService.getVersions();
    Optional<Versions.Version> validatedVersion;
    if (versions != null) {
      validatedVersion = versions.getVersion(version);
    } else {
      validatedVersion = Optional.empty();
    }

    if (validatedVersion.isPresent()) {
      String changelog = validatedVersion.get().getChangelog();
      bindings.put("changelog.gist.id", changelog.substring(changelog.lastIndexOf("/") + 1));
      bindings.put("changelog.gist.name", "changelog.md");
    } else {
      bindings.put("changelog.gist.id", "");
      bindings.put("changelog.gist.name", "");
    }

    // Configure feature-flags
    bindings.put("features.auth", Boolean.toString(features.isAuth(deploymentConfiguration)));
    bindings.put("features.chaos", Boolean.toString(features.isChaos()));
    bindings.put(
        "features.fiat",
        Boolean.toString(deploymentConfiguration.getSecurity().getAuthz().isEnabled()));
    bindings.put(
        "features.pipelineTemplates",
        Boolean.toString(
            features.getPipelineTemplates() != null ? features.getPipelineTemplates() : false));
    bindings.put(
        "features.artifacts",
        Boolean.toString(features.getArtifacts() != null ? features.getArtifacts() : false));
    bindings.put(
        "features.artifactsRewrite",
        Boolean.toString(
            features.getArtifactsRewrite() != null ? features.getArtifactsRewrite() : false));
    bindings.put(
        "features.mineCanary",
        Boolean.toString(features.getMineCanary() != null ? features.getMineCanary() : false));
    bindings.put(
        "features.appengineContainerImageUrlDeployments",
        Boolean.toString(
            features.getAppengineContainerImageUrlDeployments() != null
                ? features.getAppengineContainerImageUrlDeployments()
                : false));
    bindings.put(
        "features.travis",
        Boolean.toString(features.getTravis() != null ? features.getTravis() : false));
    bindings.put(
        "features.wercker",
        Boolean.toString(features.getWercker() != null ? features.getWercker() : false));
    bindings.put(
        "features.managedPipelineTemplatesV2UI",
        Boolean.toString(
            features.getManagedPipelineTemplatesV2UI() != null
                ? features.getManagedPipelineTemplatesV2UI()
                : false));
    bindings.put(
        "features.gremlin",
        Boolean.toString(features.getGremlin() != null ? features.getGremlin() : false));
    bindings.put(
        "features.infrastructureStages",
        Boolean.toString(
            features.getInfrastructureStages() != null
                ? features.getInfrastructureStages()
                : false));

    // Configure Kubernetes
    KubernetesProvider kubernetesProvider = deploymentConfiguration.getProviders().getKubernetes();
    bindings.put("kubernetes.default.account", kubernetesProvider.getPrimaryAccount());
    bindings.put("kubernetes.default.namespace", "default");
    bindings.put("kubernetes.default.proxy", "localhost:8001");

    // Configure GCE
    GoogleProvider googleProvider = deploymentConfiguration.getProviders().getGoogle();
    bindings.put("google.default.account", googleProvider.getPrimaryAccount());
    bindings.put("google.default.region", "us-central1");
    bindings.put("google.default.zone", "us-central1-f");

    // Configure Azure
    AzureProvider azureProvider = deploymentConfiguration.getProviders().getAzure();
    bindings.put("azure.default.account", azureProvider.getPrimaryAccount());
    bindings.put("azure.default.region", "westus");

    // Configure Appengine
    AppengineProvider appengineProvider = deploymentConfiguration.getProviders().getAppengine();
    bindings.put("appengine.default.account", appengineProvider.getPrimaryAccount());
    bindings.put(
        "appengine.enabled", Boolean.toString(appengineProvider.getPrimaryAccount() != null));

    // Configure DC/OS
    final DCOSProvider dcosProvider = deploymentConfiguration.getProviders().getDcos();
    bindings.put("dcos.default.account", dcosProvider.getPrimaryAccount());
    // TODO(willgorman) need to set the proxy url somehow

    // Configure AWS
    AwsProvider awsProvider = deploymentConfiguration.getProviders().getAws();
    bindings.put("aws.default.account", awsProvider.getPrimaryAccount());
    if (awsProvider.getPrimaryAccount() != null) {
      AwsAccount awsAccount =
          (AwsAccount)
              accountService.getProviderAccount(
                  deploymentConfiguration.getName(), "aws", awsProvider.getPrimaryAccount());
      List<AwsProvider.AwsRegion> regionList = awsAccount.getRegions();
      if (!regionList.isEmpty() && regionList.get(0) != null) {
        bindings.put("aws.default.region", regionList.get(0).getName());
      }
    }

    // Configure ECS
    EcsProvider ecsProvider = deploymentConfiguration.getProviders().getEcs();
    bindings.put("ecs.default.account", ecsProvider.getPrimaryAccount());

    // Configure CloudFoundry
    CloudFoundryProvider cloudFoundryProvider =
        deploymentConfiguration.getProviders().getCloudfoundry();
    bindings.put("cloudfoundry.default.account", cloudFoundryProvider.getPrimaryAccount());

    // Configure HuaweiCloud
    HuaweiCloudProvider huaweiCloudProvider =
        deploymentConfiguration.getProviders().getHuaweicloud();
    bindings.put("huaweicloud.default.account", huaweiCloudProvider.getPrimaryAccount());
    if (huaweiCloudProvider.getPrimaryAccount() != null) {
      HuaweiCloudAccount huaweiCloudAccount =
          (HuaweiCloudAccount)
              accountService.getProviderAccount(
                  deploymentConfiguration.getName(),
                  "huaweicloud",
                  huaweiCloudProvider.getPrimaryAccount());
      List<String> regionList = huaweiCloudAccount.getRegions();
      if (!regionList.isEmpty()) {
        bindings.put("huaweicloud.default.region", regionList.get(0));
      }
    }

    // Configure TencentCloud
    TencentCloudProvider tencentCloudProvider =
        deploymentConfiguration.getProviders().getTencentcloud();
    bindings.put("tencentcloud.default.account", tencentCloudProvider.getPrimaryAccount());
    if (tencentCloudProvider.getPrimaryAccount() != null) {
      TencentCloudAccount tencentCloudAccount =
          (TencentCloudAccount)
              accountService.getProviderAccount(
                  deploymentConfiguration.getName(),
                  "tencentcloud",
                  tencentCloudProvider.getPrimaryAccount());
      List<String> regionList = tencentCloudAccount.getRegions();
      if (!regionList.isEmpty() && regionList.get(0) != null) {
        bindings.put("tencentcloud.default.region", regionList.get(0));
      }
    }

    // Configure notifications
    bindings.put("notifications.enabled", notifications.isEnabled() + "");

    SlackNotification slackNotification = notifications.getSlack();
    bindings.put("notifications.slack.enabled", slackNotification.isEnabled() + "");
    bindings.put("notifications.slack.botName", slackNotification.getBotName());

    TwilioNotification twilioNotification = notifications.getTwilio();
    bindings.put("notifications.twilio.enabled", twilioNotification.isEnabled() + "");

    GithubStatusNotification githubStatusNotification = notifications.getGithubStatus();
    bindings.put("notifications.github-status.enabled", githubStatusNotification.isEnabled() + "");

    // Configure canary
    Canary canary = deploymentConfiguration.getCanary();
    bindings.put("canary.atlasWebComponentsUrl", canary.getAtlasWebComponentsUrl());
    bindings.put("canary.featureEnabled", Boolean.toString(canary.isEnabled()));
    if (canary.isEnabled()) {
      // TODO(duftler): Automatically choose the first metrics/storage/judge here if unspecified?
      bindings.put("canary.reduxLogger", canary.isReduxLoggerEnabled());
      bindings.put("canary.defaultMetricsAccount", canary.getDefaultMetricsAccount());
      bindings.put("canary.defaultStorageAccount", canary.getDefaultStorageAccount());
      bindings.put("canary.defaultJudge", canary.getDefaultJudge());
      bindings.put("canary.defaultMetricsStore", canary.getDefaultMetricsStore());
      bindings.put("canary.stages", canary.isStagesEnabled());
      bindings.put("canary.templatesEnabled", canary.isTemplatesEnabled());
      bindings.put("canary.showAllCanaryConfigs", canary.isShowAllConfigsEnabled());
    }

    profile
        .appendContents(configTemplate.setBindings(bindings).toString())
        .setRequiredFiles(backupRequiredFiles(uiSecurity, deploymentConfiguration.getName()));
  }
}
