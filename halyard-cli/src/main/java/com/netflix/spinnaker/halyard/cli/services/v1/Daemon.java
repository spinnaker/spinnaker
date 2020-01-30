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

package com.netflix.spinnaker.halyard.cli.services.v1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.cli.command.v1.GlobalOptions;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.ArtifactTemplate;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.ci.CiType;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaService;
import com.netflix.spinnaker.halyard.config.model.v1.ha.HaServices;
import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.PluginRepository;
import com.netflix.spinnaker.halyard.config.model.v1.security.*;
import com.netflix.spinnaker.halyard.config.model.v1.webook.WebhookTrust;
import com.netflix.spinnaker.halyard.core.DaemonOptions;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.StringBodyRequest;
import com.netflix.spinnaker.halyard.core.registry.v1.BillOfMaterials;
import com.netflix.spinnaker.halyard.core.registry.v1.Versions;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.ShallowTaskList;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeployOption;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.RunningServiceDetails;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

@Slf4j
public class Daemon {
  public static boolean isHealthy() {
    return getService().getHealth().get("status").equalsIgnoreCase("up");
  }

  public static String shutdown() {
    return getService().shutdown(new StringBodyRequest()).getOrDefault("message", "");
  }

  public static ShallowTaskList getTasks() {
    return getService().getTasks();
  }

  public static Supplier<String> createBackup() {
    return () -> {
      Object rawBackupResponse = ResponseUnwrapper.get(getService().createBackup(""));
      return objectMapper.convertValue(rawBackupResponse, StringBodyRequest.class).getValue();
    };
  }

  public static Supplier<Void> restoreBackup(String path) {
    return () -> {
      ResponseUnwrapper.get(getService().restoreBackup(path, ""));
      return null;
    };
  }

  public static Supplier<List<DeploymentConfiguration>> getDeployments() {
    return () -> ResponseUnwrapper.get(getService().getDeployments());
  }

  public static Supplier<String> getCurrentDeployment() {
    return () -> ResponseUnwrapper.get(getService().getCurrentDeployment());
  }

  public static Supplier<Void> setCurrentDeployment(String name) {
    return () ->
        ResponseUnwrapper.get(getService().setCurrentDeployment(new StringBodyRequest(name)));
  }

  public static Supplier<DeploymentConfiguration> getDeploymentConfiguration(
      String deploymentName, boolean validate) {
    return () -> {
      Object rawDeploymentConfiguration =
          ResponseUnwrapper.get(getService().getDeployment(deploymentName, validate));
      return getObjectMapper()
          .convertValue(rawDeploymentConfiguration, DeploymentConfiguration.class);
    };
  }

  public static Supplier<Void> setDeploymentConfiguration(
      String deploymentName, boolean validate, DeploymentConfiguration deploymentConfiguration) {
    return () ->
        ResponseUnwrapper.get(
            getService().setDeployment(deploymentName, validate, deploymentConfiguration));
  }

  public static Supplier<DeploymentEnvironment> getDeploymentEnvironment(
      String deploymentName, boolean validate) {
    return () -> {
      Object rawDeploymentEnvironment =
          ResponseUnwrapper.get(getService().getDeploymentEnvironment(deploymentName, validate));
      return getObjectMapper().convertValue(rawDeploymentEnvironment, DeploymentEnvironment.class);
    };
  }

  public static Supplier<Void> setDeploymentEnvironment(
      String deploymentName, boolean validate, DeploymentEnvironment deploymentEnvironment) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setDeploymentEnvironment(deploymentName, validate, deploymentEnvironment));
      return null;
    };
  }

  public static Supplier<Void> setHaService(
      String deploymentName, String serviceName, boolean validate, HaService haService) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setHaService(deploymentName, serviceName, validate, haService));
      return null;
    };
  }

  public static Supplier<HaService> getHaService(
      String deploymentName, String serviceName, boolean validate) {
    return () -> {
      Object haService =
          ResponseUnwrapper.get(getService().getHaService(deploymentName, serviceName, validate));
      return getObjectMapper()
          .convertValue(haService, HaServices.translateHaServiceType(serviceName));
    };
  }

  public static Supplier<Void> setHaServiceEnableDisable(
      String deploymentName, String serviceName, boolean validate, boolean enable) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setHaServiceEnabled(deploymentName, serviceName, validate, enable));
      return null;
    };
  }

  public static Supplier<BakeryDefaults> getBakeryDefaults(
      String deploymentName, String providerName, boolean validate) {
    return () -> {
      Object rawBakeryDefaults =
          ResponseUnwrapper.get(
              getService().getBakeryDefaults(deploymentName, providerName, validate));
      return getObjectMapper()
          .convertValue(rawBakeryDefaults, Providers.translateBakeryDefaultsType(providerName));
    };
  }

  public static Supplier<Void> setBakeryDefaults(
      String deploymentName, String providerName, boolean validate, BakeryDefaults bakeryDefaults) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setBakeryDefaults(deploymentName, providerName, validate, bakeryDefaults));
      return null;
    };
  }

  public static Supplier<Features> getFeatures(String deploymentName, boolean validate) {
    return () -> {
      Object rawFeatures =
          ResponseUnwrapper.get(getService().getFeatures(deploymentName, validate));
      return getObjectMapper().convertValue(rawFeatures, Features.class);
    };
  }

  public static Supplier<Void> setFeatures(
      String deploymentName, boolean validate, Features features) {
    return () -> {
      ResponseUnwrapper.get(getService().setFeatures(deploymentName, validate, features));
      return null;
    };
  }

  public static Supplier<PersistentStorage> getPersistentStorage(
      String deploymentName, boolean validate) {
    return () -> {
      Object rawPersistentStorage =
          ResponseUnwrapper.get(getService().getPersistentStorage(deploymentName, validate));
      return getObjectMapper().convertValue(rawPersistentStorage, PersistentStorage.class);
    };
  }

  public static Supplier<PersistentStore> getPersistentStore(
      String deploymentName, String persistentStoreType, boolean validate) {
    return () -> {
      Object rawPersistentStore =
          ResponseUnwrapper.get(
              getService().getPersistentStore(deploymentName, persistentStoreType, validate));
      return getObjectMapper()
          .convertValue(
              rawPersistentStore,
              PersistentStorage.translatePersistentStoreType(persistentStoreType));
    };
  }

  public static Supplier<Void> setPersistentStorage(
      String deploymentName, boolean validate, PersistentStorage persistentStorage) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setPersistentStorage(deploymentName, validate, persistentStorage));
      return null;
    };
  }

  public static Supplier<Void> setPersistentStore(
      String deploymentName,
      String persistentStoreType,
      boolean validate,
      PersistentStore persistentStore) {
    return () -> {
      ResponseUnwrapper.get(
          getService()
              .setPersistentStore(deploymentName, persistentStoreType, validate, persistentStore));
      return null;
    };
  }

  public static Supplier<BaseImage> getBaseImage(
      String deploymentName, String providerName, String baseImageId, boolean validate) {
    return () -> {
      Object rawBaseImage =
          ResponseUnwrapper.get(
              getService().getBaseImage(deploymentName, providerName, baseImageId, validate));
      return getObjectMapper()
          .convertValue(rawBaseImage, Providers.translateBaseImageType(providerName));
    };
  }

  public static Supplier<Void> addBaseImage(
      String deploymentName, String providerName, boolean validate, BaseImage baseImage) {
    return () -> {
      ResponseUnwrapper.get(
          getService().addBaseImage(deploymentName, providerName, validate, baseImage));
      return null;
    };
  }

  public static Supplier<Void> setBaseImage(
      String deploymentName,
      String providerName,
      String baseImageId,
      boolean validate,
      BaseImage baseImage) {
    return () -> {
      ResponseUnwrapper.get(
          getService()
              .setBaseImage(deploymentName, providerName, baseImageId, validate, baseImage));
      return null;
    };
  }

  public static Supplier<Void> deleteBaseImage(
      String deploymentName, String providerName, String baseImageId, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(
          getService().deleteBaseImage(deploymentName, providerName, baseImageId, validate));
      return null;
    };
  }

  public static Supplier<Subscription> getSubscription(
      String deploymentName, String pubsubName, String subscriptionName, boolean validate) {
    return () -> {
      Object rawSubscription =
          ResponseUnwrapper.get(
              getService().getSubscription(deploymentName, pubsubName, subscriptionName, validate));
      return getObjectMapper()
          .convertValue(rawSubscription, Pubsubs.translateSubscriptionType(pubsubName));
    };
  }

  public static Supplier<Void> addSubscription(
      String deploymentName, String pubsubName, boolean validate, Subscription subscription) {
    return () -> {
      ResponseUnwrapper.get(
          getService().addSubscription(deploymentName, pubsubName, validate, subscription));
      return null;
    };
  }

  public static Supplier<Void> setSubscription(
      String deploymentName,
      String pubsubName,
      String subscriptionName,
      boolean validate,
      Subscription subscription) {
    return () -> {
      ResponseUnwrapper.get(
          getService()
              .setSubscription(
                  deploymentName, pubsubName, subscriptionName, validate, subscription));
      return null;
    };
  }

  public static Supplier<Void> deleteSubscription(
      String deploymentName, String pubsubName, String subscriptionName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(
          getService().deleteSubscription(deploymentName, pubsubName, subscriptionName, validate));
      return null;
    };
  }

  public static Supplier<Account> getAccount(
      String deploymentName, String providerName, String accountName, boolean validate) {
    return () -> {
      Object rawAccount =
          ResponseUnwrapper.get(
              getService().getAccount(deploymentName, providerName, accountName, validate));
      return getObjectMapper()
          .convertValue(rawAccount, Providers.translateAccountType(providerName));
    };
  }

  public static Supplier<Void> addAccount(
      String deploymentName, String providerName, boolean validate, Account account) {
    return () -> {
      ResponseUnwrapper.get(
          getService().addAccount(deploymentName, providerName, validate, account));
      return null;
    };
  }

  public static Supplier<Void> setAccount(
      String deploymentName,
      String providerName,
      String accountName,
      boolean validate,
      Account account) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setAccount(deploymentName, providerName, accountName, validate, account));
      return null;
    };
  }

  public static Supplier<Void> deleteAccount(
      String deploymentName, String providerName, String accountName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(
          getService().deleteAccount(deploymentName, providerName, accountName, validate));
      return null;
    };
  }

  public static Supplier<AbstractCanaryAccount> getCanaryAccount(
      String deploymentName, String serviceIntegrationName, String accountName, boolean validate) {
    return () -> {
      Object rawAccount =
          ResponseUnwrapper.get(
              getService()
                  .getCanaryAccount(deploymentName, serviceIntegrationName, accountName, validate));
      return getObjectMapper()
          .convertValue(rawAccount, Canary.translateCanaryAccountType(serviceIntegrationName));
    };
  }

  public static Supplier<Void> addCanaryAccount(
      String deploymentName,
      String serviceIntegrationName,
      boolean validate,
      AbstractCanaryAccount account) {
    return () -> {
      ResponseUnwrapper.get(
          getService().addCanaryAccount(deploymentName, serviceIntegrationName, validate, account));
      return null;
    };
  }

  public static Supplier<Void> setCanaryAccount(
      String deploymentName,
      String serviceIntegrationName,
      String accountName,
      boolean validate,
      AbstractCanaryAccount account) {
    return () -> {
      ResponseUnwrapper.get(
          getService()
              .setCanaryAccount(
                  deploymentName, serviceIntegrationName, accountName, validate, account));
      return null;
    };
  }

  public static Supplier<Void> deleteCanaryAccount(
      String deploymentName, String serviceIntegrationName, String accountName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(
          getService()
              .deleteCanaryAccount(deploymentName, serviceIntegrationName, accountName, validate));
      return null;
    };
  }

  public static Supplier<ArtifactAccount> getArtifactAccount(
      String deploymentName, String providerName, String accountName, boolean validate) {
    return () -> {
      Object rawArtifactAccount =
          ResponseUnwrapper.get(
              getService().getArtifactAccount(deploymentName, providerName, accountName, validate));
      return getObjectMapper()
          .convertValue(rawArtifactAccount, Artifacts.translateArtifactAccountType(providerName));
    };
  }

  public static Supplier<Void> addArtifactAccount(
      String deploymentName, String providerName, boolean validate, ArtifactAccount account) {
    return () -> {
      ResponseUnwrapper.get(
          getService().addArtifactAccount(deploymentName, providerName, validate, account));
      return null;
    };
  }

  public static Supplier<Void> setArtifactAccount(
      String deploymentName,
      String providerName,
      String accountName,
      boolean validate,
      ArtifactAccount account) {
    return () -> {
      ResponseUnwrapper.get(
          getService()
              .setArtifactAccount(deploymentName, providerName, accountName, validate, account));
      return null;
    };
  }

  public static Supplier<Void> deleteArtifactAccount(
      String deploymentName, String providerName, String accountName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(
          getService().deleteArtifactAccount(deploymentName, providerName, accountName, validate));
      return null;
    };
  }

  public static Supplier<List<String>> getNewAccountOptions(
      String deploymentName, String providerName, String fieldName, Account account) {
    return () -> {
      DaemonOptions<Account> accountOptions =
          new DaemonOptions<Account>().setField(fieldName).setResource(account);
      return ResponseUnwrapper.get(
          getService().getNewAccountOptions(deploymentName, providerName, accountOptions));
    };
  }

  public static Supplier<List<String>> getExistingAccountOptions(
      String deploymentName, String providerName, String accountName, String fieldName) {
    return () -> {
      DaemonOptions<Void> accountOptions = new DaemonOptions<Void>().setField(fieldName);
      return ResponseUnwrapper.get(
          getService()
              .getExistingAccountOptions(
                  deploymentName, providerName, accountName, accountOptions));
    };
  }

  public static Supplier<Cluster> getCluster(
      String deploymentName, String providerName, String clusterName, boolean validate) {
    return () -> {
      Object rawCluster =
          ResponseUnwrapper.get(
              getService().getCluster(deploymentName, providerName, clusterName, validate));
      return getObjectMapper()
          .convertValue(rawCluster, Providers.translateClusterType(providerName));
    };
  }

  public static Supplier<Void> addCluster(
      String deploymentName, String providerName, boolean validate, Cluster cluster) {
    return () -> {
      ResponseUnwrapper.get(
          getService().addCluster(deploymentName, providerName, validate, cluster));
      return null;
    };
  }

  public static Supplier<Void> setCluster(
      String deploymentName,
      String providerName,
      String clusterName,
      boolean validate,
      Cluster cluster) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setCluster(deploymentName, providerName, clusterName, validate, cluster));
      return null;
    };
  }

  public static Supplier<Void> deleteCluster(
      String deploymentName, String providerName, String clusterName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(
          getService().deleteCluster(deploymentName, providerName, clusterName, validate));
      return null;
    };
  }

  public static Supplier<Publisher> getPublisher(
      String deploymentName, String pubsubName, String publisherName, boolean validate) {
    return () -> {
      Object rawPublisher =
          ResponseUnwrapper.get(
              getService().getPublisher(deploymentName, pubsubName, publisherName, validate));
      return getObjectMapper()
          .convertValue(rawPublisher, Pubsubs.translatePublisherType(pubsubName));
    };
  }

  public static Supplier<Void> addPublisher(
      String deploymentName, String pubsubName, boolean validate, Publisher publisher) {
    return () -> {
      ResponseUnwrapper.get(
          getService().addPublisher(deploymentName, pubsubName, validate, publisher));
      return null;
    };
  }

  public static Supplier<Void> setPublisher(
      String deploymentName,
      String pubsubName,
      String publisherName,
      boolean validate,
      Publisher publisher) {
    return () -> {
      ResponseUnwrapper.get(
          getService()
              .setPublisher(deploymentName, pubsubName, publisherName, validate, publisher));
      return null;
    };
  }

  public static Supplier<Void> deletePublisher(
      String deploymentName, String pubsubName, String publisherName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(
          getService().deletePublisher(deploymentName, pubsubName, publisherName, validate));
      return null;
    };
  }

  public static Supplier<Void> setPubsub(
      String deploymentName, String pubsubName, boolean validate, Pubsub pubsub) {
    return () -> {
      ResponseUnwrapper.get(getService().setPubsub(deploymentName, pubsubName, validate, pubsub));
      return null;
    };
  }

  public static Supplier<Pubsub> getPubsub(
      String deploymentName, String pubsubName, boolean validate) {
    return () -> {
      Object pubsub =
          ResponseUnwrapper.get(getService().getPubsub(deploymentName, pubsubName, validate));
      return getObjectMapper().convertValue(pubsub, Pubsubs.translatePubsubType(pubsubName));
    };
  }

  public static Supplier<Void> setPubsubEnableDisable(
      String deploymentName, String pubsubName, boolean validate, boolean enable) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setPubsubEnabled(deploymentName, pubsubName, validate, enable));
      return null;
    };
  }

  public static Supplier<Void> setProvider(
      String deploymentName, String providerName, boolean validate, Provider provider) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setProvider(deploymentName, providerName, validate, provider));
      return null;
    };
  }

  public static Supplier<Provider> getProvider(
      String deploymentName, String providerName, boolean validate) {
    return () -> {
      Object provider =
          ResponseUnwrapper.get(getService().getProvider(deploymentName, providerName, validate));
      return getObjectMapper()
          .convertValue(provider, Providers.translateProviderType(providerName));
    };
  }

  public static Supplier<Void> setProviderEnableDisable(
      String deploymentName, String providerName, boolean validate, boolean enable) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setProviderEnabled(deploymentName, providerName, validate, enable));
      return null;
    };
  }

  public static Supplier<Void> setArtifactProvider(
      String deploymentName, String providerName, boolean validate, ArtifactProvider provider) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setArtifactProvider(deploymentName, providerName, validate, provider));
      return null;
    };
  }

  public static Supplier<ArtifactProvider> getArtifactProvider(
      String deploymentName, String providerName, boolean validate) {
    return () -> {
      Object provider =
          ResponseUnwrapper.get(
              getService().getArtifactProvider(deploymentName, providerName, validate));
      return getObjectMapper()
          .convertValue(provider, Artifacts.translateArtifactProviderType(providerName));
    };
  }

  public static Supplier<Void> setArtifactProviderEnableDisable(
      String deploymentName, String providerName, boolean validate, boolean enable) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setArtifactProviderEnabled(deploymentName, providerName, validate, enable));
      return null;
    };
  }

  public static Supplier<Notifications> getNotifications(String deploymentName, boolean validate) {
    return () -> {
      Object notification =
          ResponseUnwrapper.get(getService().getNotifications(deploymentName, validate));
      return getObjectMapper().convertValue(notification, Notifications.class);
    };
  }

  public static Supplier<Void> setNotification(
      String deploymentName, String notificationName, boolean validate, Notification notification) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setNotification(deploymentName, notificationName, validate, notification));
      return null;
    };
  }

  public static Supplier<Notification> getNotification(
      String deploymentName, String notificationName, boolean validate) {
    return () -> {
      Object notification =
          ResponseUnwrapper.get(
              getService().getNotification(deploymentName, notificationName, validate));
      return getObjectMapper()
          .convertValue(notification, Notifications.translateNotificationType(notificationName));
    };
  }

  public static Supplier<Void> setNotificationEnabled(
      String deploymentName, String notificationName, boolean validate, boolean enable) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setNotificationEnabled(deploymentName, notificationName, validate, enable));
      return null;
    };
  }

  public static Supplier<CIAccount> getMaster(
      String deploymentName, String ciName, String masterName, boolean validate) {
    return () -> {
      Object rawMaster =
          ResponseUnwrapper.get(
              getService().getMaster(deploymentName, ciName, masterName, validate));
      return getObjectMapper().convertValue(rawMaster, CiType.getCiType(ciName).accountClass);
    };
  }

  public static Supplier<Void> addMaster(
      String deploymentName, String ciName, boolean validate, CIAccount account) {
    return () -> {
      ResponseUnwrapper.get(getService().addMaster(deploymentName, ciName, validate, account));
      return null;
    };
  }

  public static Supplier<Void> setMaster(
      String deploymentName,
      String ciName,
      String masterName,
      boolean validate,
      CIAccount account) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setMaster(deploymentName, ciName, masterName, validate, account));
      return null;
    };
  }

  public static Supplier<Void> deleteMaster(
      String deploymentName, String ciName, String masterName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(
          getService().deleteMaster(deploymentName, ciName, masterName, validate));
      return null;
    };
  }

  public static Supplier<Ci> getCi(String deploymentName, String ciName, boolean validate) {
    return () -> {
      Object ci = ResponseUnwrapper.get(getService().getCi(deploymentName, ciName, validate));
      return getObjectMapper().convertValue(ci, CiType.getCiType(ciName).ciClass);
    };
  }

  public static Supplier<Void> setCiEnableDisable(
      String deploymentName, String ciName, boolean validate, boolean enable) {
    return () -> {
      ResponseUnwrapper.get(getService().setCiEnabled(deploymentName, ciName, validate, enable));
      return null;
    };
  }

  public static Supplier<Repository> getRepository(
      String deploymentName, String repositoryName, boolean validate) {
    return () -> {
      Object repository =
          ResponseUnwrapper.get(
              getService().getRepository(deploymentName, repositoryName, validate));
      return getObjectMapper()
          .convertValue(repository, Repositories.translateReposiroryType(repositoryName));
    };
  }

  public static Supplier<Void> setRepositoryEnableDisable(
      String deploymentName, String repositoryName, boolean validate, boolean enable) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setRepositoryEnabled(deploymentName, repositoryName, validate, enable));
      return null;
    };
  }

  public static Supplier<Search> getSearch(
      String deploymentName, String repositoryName, String searchName, boolean validate) {
    return () -> {
      Object rawSearch =
          ResponseUnwrapper.get(
              getService().getSearch(deploymentName, repositoryName, searchName, validate));
      return getObjectMapper()
          .convertValue(rawSearch, Repositories.translateSearchType(repositoryName));
    };
  }

  public static Supplier<Void> addSearch(
      String deploymentName, String repositoryName, boolean validate, Search search) {
    return () -> {
      ResponseUnwrapper.get(
          getService().addSearch(deploymentName, repositoryName, validate, search));
      return null;
    };
  }

  public static Supplier<Void> setSearch(
      String deploymentName,
      String repositoryName,
      String searchName,
      boolean validate,
      Search search) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setSearch(deploymentName, repositoryName, searchName, validate, search));
      return null;
    };
  }

  public static Supplier<Void> deleteSearch(
      String deploymentName, String repositoryName, String searchName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(
          getService().deleteSearch(deploymentName, repositoryName, searchName, validate));
      return null;
    };
  }

  public static Supplier<String> generateDeployment(String deploymentName, boolean validate) {
    return () -> {
      return ResponseUnwrapper.get(getService().generateDeployment(deploymentName, validate, ""));
    };
  }

  public static Supplier<RemoteAction> connectToDeployment(
      String deploymentName, boolean validate, List<String> serviceNames) {
    return () -> {
      Object rawDeployResult =
          ResponseUnwrapper.get(
              getService().connectToDeployment(deploymentName, validate, serviceNames, ""));
      return getObjectMapper().convertValue(rawDeployResult, RemoteAction.class);
    };
  }

  public static Supplier<RemoteAction> prepDeployment(
      String deploymentName,
      boolean validate,
      List<String> serviceNames,
      List<String> excludeServiceNames) {
    return () -> {
      Object rawDeployResult =
          ResponseUnwrapper.get(
              getService()
                  .prepDeployment(deploymentName, validate, serviceNames, excludeServiceNames, ""));
      return getObjectMapper().convertValue(rawDeployResult, RemoteAction.class);
    };
  }

  public static Supplier<RemoteAction> deployDeployment(
      String deploymentName,
      boolean validate,
      List<DeployOption> deployOptions,
      List<String> serviceNames,
      List<String> excludeServiceNames,
      Integer waitForCompletionTimeoutMinutes) {
    return () -> {
      Object rawDeployResult =
          ResponseUnwrapper.get(
              getService()
                  .deployDeployment(
                      deploymentName,
                      validate,
                      deployOptions,
                      serviceNames,
                      excludeServiceNames,
                      waitForCompletionTimeoutMinutes,
                      ""));
      return getObjectMapper().convertValue(rawDeployResult, RemoteAction.class);
    };
  }

  public static Supplier<Void> collectLogs(
      String deploymentName,
      boolean validate,
      List<String> serviceNames,
      List<String> excludeServiceNames) {
    return () -> {
      ResponseUnwrapper.get(
          getService()
              .collectLogs(deploymentName, validate, serviceNames, excludeServiceNames, ""));
      return null;
    };
  }

  public static Supplier<Void> cleanDeployment(String deploymentName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(getService().cleanDeployment(deploymentName, validate, ""));
      return null;
    };
  }

  public static Supplier<Void> rollbackDeployment(
      String deploymentName,
      boolean validate,
      List<String> serviceNames,
      List<String> excludeServiceNames) {
    return () -> {
      ResponseUnwrapper.get(
          getService()
              .rollbackDeployment(deploymentName, validate, serviceNames, excludeServiceNames, ""));
      return null;
    };
  }

  public static Supplier<NodeDiff> configDiff(String deploymentName, boolean validate) {
    return () -> {
      Object rawDiff = ResponseUnwrapper.get(getService().configDiff(deploymentName, validate));
      return getObjectMapper().convertValue(rawDiff, NodeDiff.class);
    };
  }

  public static Supplier<MetricStores> getMetricStores(String deploymentName, boolean validate) {
    return () -> {
      Object rawMetricStores =
          ResponseUnwrapper.get(getService().getMetricStores(deploymentName, validate));
      return getObjectMapper().convertValue(rawMetricStores, MetricStores.class);
    };
  }

  public static Supplier<Void> setMetricStores(
      String deploymentName, boolean validate, MetricStores metricStores) {
    return () -> {
      ResponseUnwrapper.get(getService().setMetricStores(deploymentName, validate, metricStores));
      return null;
    };
  }

  public static Supplier<MetricStore> getMetricStore(
      String deploymentName, String metricStoreType, boolean validate) {
    return () -> {
      Object rawMetricStore =
          ResponseUnwrapper.get(
              getService().getMetricStore(deploymentName, metricStoreType, validate));
      return getObjectMapper()
          .convertValue(rawMetricStore, MetricStores.translateMetricStoreType(metricStoreType));
    };
  }

  public static Supplier<Void> setMetricStore(
      String deploymentName, String metricStoreType, boolean validate, MetricStore metricStore) {
    return () -> {
      Map translatedMetricStore = getObjectMapper().convertValue(metricStore, Map.class);
      ResponseUnwrapper.get(
          getService()
              .setMetricStore(deploymentName, metricStoreType, validate, translatedMetricStore));
      return null;
    };
  }

  public static Supplier<Void> setMetricStoreEnabled(
      String deploymentName, String metricStoreType, boolean validate, boolean enabled) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setMetricStoreEnabled(deploymentName, metricStoreType, validate, enabled));
      return null;
    };
  }

  public static Supplier<Security> getSecurity(String deploymentName, boolean validate) {
    return () -> {
      Object rawSecurity =
          ResponseUnwrapper.get(getService().getSecurity(deploymentName, validate));
      return getObjectMapper().convertValue(rawSecurity, Security.class);
    };
  }

  public static Supplier<Void> setSecurity(
      String deploymentName, boolean validate, Security security) {
    return () -> {
      ResponseUnwrapper.get(getService().setSecurity(deploymentName, validate, security));
      return null;
    };
  }

  public static Supplier<ApiSecurity> getApiSecurity(String deploymentName, boolean validate) {
    return () -> {
      Object rawApiSecurity =
          ResponseUnwrapper.get(getService().getApiSecurity(deploymentName, validate));
      return getObjectMapper().convertValue(rawApiSecurity, ApiSecurity.class);
    };
  }

  public static Supplier<Void> setApiSecurity(
      String deploymentName, boolean validate, ApiSecurity apiSecurity) {
    return () -> {
      ResponseUnwrapper.get(getService().setApiSecurity(deploymentName, validate, apiSecurity));
      return null;
    };
  }

  public static Supplier<SpringSsl> getSpringSsl(String deploymentName, boolean validate) {
    return () -> {
      Object rawSpringSsl =
          ResponseUnwrapper.get(getService().getSpringSsl(deploymentName, validate));
      return getObjectMapper().convertValue(rawSpringSsl, SpringSsl.class);
    };
  }

  public static Supplier<Void> setSpringSsl(
      String deploymentName, boolean validate, SpringSsl apacheSsl) {
    return () -> {
      ResponseUnwrapper.get(getService().setSpringSsl(deploymentName, validate, apacheSsl));
      return null;
    };
  }

  public static Supplier<Void> setSpringSslEnabled(
      String deploymentName, boolean validate, boolean enabled) {
    return () -> {
      ResponseUnwrapper.get(getService().setSpringSslEnabled(deploymentName, validate, enabled));
      return null;
    };
  }

  public static Supplier<UiSecurity> getUiSecurity(String deploymentName, boolean validate) {
    return () -> {
      Object rawUiSecurity =
          ResponseUnwrapper.get(getService().getUiSecurity(deploymentName, validate));
      return getObjectMapper().convertValue(rawUiSecurity, UiSecurity.class);
    };
  }

  public static Supplier<Void> setUiSecurity(
      String deploymentName, boolean validate, UiSecurity uiSecurity) {
    return () -> {
      ResponseUnwrapper.get(getService().setUiSecurity(deploymentName, validate, uiSecurity));
      return null;
    };
  }

  public static Supplier<ApacheSsl> getApacheSsl(String deploymentName, boolean validate) {
    return () -> {
      Object rawApacheSsl =
          ResponseUnwrapper.get(getService().getApacheSsl(deploymentName, validate));
      return getObjectMapper().convertValue(rawApacheSsl, ApacheSsl.class);
    };
  }

  public static Supplier<Void> setApacheSsl(
      String deploymentName, boolean validate, ApacheSsl apacheSsl) {
    return () -> {
      ResponseUnwrapper.get(getService().setApacheSsl(deploymentName, validate, apacheSsl));
      return null;
    };
  }

  public static Supplier<Void> setApacheSslEnabled(
      String deploymentName, boolean validate, boolean enabled) {
    return () -> {
      ResponseUnwrapper.get(getService().setApacheSslEnabled(deploymentName, validate, enabled));
      return null;
    };
  }

  public static Supplier<AuthnMethod> getAuthnMethod(
      String deploymentName, String methodName, boolean validate) {
    return () -> {
      Object rawOAuth2 =
          ResponseUnwrapper.get(getService().getAuthnMethod(deploymentName, methodName, validate));
      return getObjectMapper()
          .convertValue(rawOAuth2, AuthnMethod.translateAuthnMethodName(methodName));
    };
  }

  public static Supplier<Void> setAuthnMethod(
      String deploymentName, String methodName, boolean validate, AuthnMethod authnMethod) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setAuthnMethod(deploymentName, methodName, validate, authnMethod));
      return null;
    };
  }

  public static Supplier<Void> setGroupMembership(
      String deploymentName, boolean validate, GroupMembership membership) {
    return () -> {
      ResponseUnwrapper.get(getService().setGroupMembership(deploymentName, validate, membership));
      return null;
    };
  }

  public static Supplier<GroupMembership> getGroupMembership(
      String deploymentName, boolean validate) {
    return () -> {
      Object rawGroupMembership =
          ResponseUnwrapper.get(getService().getGroupMembership(deploymentName, validate));
      return getObjectMapper().convertValue(rawGroupMembership, GroupMembership.class);
    };
  }

  public static Supplier<RoleProvider> getRoleProvider(
      String deploymentName, String roleProviderName, boolean validate) {
    return () -> {
      Object rawRoleProvider =
          ResponseUnwrapper.get(
              getService().getRoleProvider(deploymentName, roleProviderName, validate));
      return getObjectMapper()
          .convertValue(
              rawRoleProvider, GroupMembership.translateRoleProviderType(roleProviderName));
    };
  }

  public static Supplier<Void> setRoleProvider(
      String deploymentName, String roleProviderName, boolean validate, RoleProvider authnMethod) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setRoleProvider(deploymentName, roleProviderName, validate, authnMethod));
      return null;
    };
  }

  public static Supplier<Void> setAuthzEnabled(
      String deploymentName, boolean validate, boolean enabled) {
    return () -> {
      ResponseUnwrapper.get(getService().setAuthzEnabled(deploymentName, validate, enabled));
      return null;
    };
  }

  public static Supplier<Void> setAuthnMethodEnabled(
      String deploymentName, String methodName, boolean validate, boolean enabled) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setAuthnMethodEnabled(deploymentName, methodName, validate, enabled));
      return null;
    };
  }

  public static Supplier<Canary> getCanary(String deploymentName, boolean validate) {
    return () -> {
      Object rawCanary = ResponseUnwrapper.get(getService().getCanary(deploymentName, validate));
      return getObjectMapper().convertValue(rawCanary, Canary.class);
    };
  }

  public static Supplier<Void> setCanary(String deploymentName, boolean validate, Canary canary) {
    return () -> {
      ResponseUnwrapper.get(getService().setCanary(deploymentName, validate, canary));
      return null;
    };
  }

  public static Supplier<Void> setCanaryEnabled(
      String deploymentName, boolean validate, boolean enabled) {
    return () -> {
      ResponseUnwrapper.get(getService().setCanaryEnabled(deploymentName, validate, enabled));
      return null;
    };
  }

  public static Supplier<Versions> getVersions() {
    return () -> {
      Object rawVersions = ResponseUnwrapper.get(getService().getVersions());
      return getObjectMapper().convertValue(rawVersions, Versions.class);
    };
  }

  public static Supplier<String> getLatest() {
    return () -> ResponseUnwrapper.get(getService().getLatest());
  }

  public static Supplier<RunningServiceDetails> getServiceDetails(
      String deploymentName, String serviceName, boolean validate) {
    return () -> {
      Object rawDetails =
          ResponseUnwrapper.get(
              getService().getServiceDetails(deploymentName, serviceName, validate));
      return getObjectMapper().convertValue(rawDetails, RunningServiceDetails.class);
    };
  }

  public static Supplier<BillOfMaterials> getBillOfMaterials(String version) {
    return () -> {
      Object rawBillOfMaterials = ResponseUnwrapper.get(getService().getBillOfMaterialsV2(version));
      return getObjectMapper().convertValue(rawBillOfMaterials, BillOfMaterials.class);
    };
  }

  public static Supplier<Void> publishProfile(
      String bomPath, String artifactName, String profilePath) {
    return () -> {
      ResponseUnwrapper.get(getService().publishProfile(bomPath, artifactName, profilePath, ""));
      return null;
    };
  }

  public static Supplier<Void> publishLatestHalyard(String latestHalyard) {
    return () -> {
      ResponseUnwrapper.get(getService().publishLatestHalyard(latestHalyard, ""));
      return null;
    };
  }

  public static Supplier<Void> publishLatestSpinnaker(String latestSpinnaker) {
    return () -> {
      ResponseUnwrapper.get(getService().publishLatestSpinnaker(latestSpinnaker, ""));
      return null;
    };
  }

  public static Supplier<Void> deprecateVersion(Versions.Version version, String illegalReason) {
    return () -> {
      ResponseUnwrapper.get(getService().deprecateVersion(version, illegalReason));
      return null;
    };
  }

  public static Supplier<Void> publishVersion(Versions.Version version) {
    return () -> {
      ResponseUnwrapper.get(getService().publishVersion(version));
      return null;
    };
  }

  public static Supplier<Void> publishBom(String bomPath) {
    return () -> {
      ResponseUnwrapper.get(getService().publishBom(bomPath, ""));
      return null;
    };
  }

  public static Supplier<String> getVersion(String deploymentName, boolean validate) {
    return () -> ResponseUnwrapper.get(getService().getVersion(deploymentName, validate));
  }

  public static Supplier<Void> setVersion(
      String deploymentName, boolean validate, String versionName) {
    return () -> {
      Versions.Version version = new Versions.Version().setVersion(versionName);
      ResponseUnwrapper.get(getService().setVersion(deploymentName, validate, version));
      return null;
    };
  }

  public static Supplier<RemoteAction> installSpin() {
    return () -> {
      Object rawRemoteAction = ResponseUnwrapper.get(getService().installSpin());
      return getObjectMapper().convertValue(rawRemoteAction, RemoteAction.class);
    };
  }

  static <C, T> DaemonTask<C, T> getTask(String uuid) {
    return getService().getTask(uuid);
  }

  public static void interruptTask(String uuid) {
    getService().interruptTask(uuid, "");
  }

  private static DaemonService getService() {
    if (service == null) {
      boolean debug = GlobalOptions.getGlobalOptions().isDebug();
      service = createService(debug);
    }

    return service;
  }

  private static ObjectMapper getObjectMapper() {
    if (objectMapper == null) {
      objectMapper = new ObjectMapper();
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    return objectMapper;
  }

  public static Supplier<Webhook> getWebhook(String deploymentName, boolean validate) {
    return () -> {
      Object rawWebhook = ResponseUnwrapper.get(getService().getWebhook(deploymentName, validate));
      return getObjectMapper().convertValue(rawWebhook, Webhook.class);
    };
  }

  public static Supplier<WebhookTrust> getWebhookTrust(String deploymentName, boolean validate) {
    return () -> {
      Object rawWebhookTrust =
          ResponseUnwrapper.get(getService().getWebhookTrust(deploymentName, validate));
      return getObjectMapper().convertValue(rawWebhookTrust, WebhookTrust.class);
    };
  }

  public static Supplier<Void> setWebhookTrust(
      String deploymentName, boolean validate, WebhookTrust webhookTrust) {
    return () -> {
      Object rawWebhookTrust =
          ResponseUnwrapper.get(
              getService().setWebhookTrust(deploymentName, validate, webhookTrust));
      return null;
    };
  }

  public static Supplier<List<ArtifactTemplate>> getArtifactTemplates(
      String deploymentName, boolean validate) {
    return () -> {
      Object rawArtifactTemplate =
          ResponseUnwrapper.get(getService().getArtifactTemplates(deploymentName, validate));
      return getObjectMapper()
          .convertValue(rawArtifactTemplate, new TypeReference<List<ArtifactTemplate>>() {});
    };
  }

  public static Supplier<ArtifactTemplate> getArtifactTemplate(
      String deploymentName, String templateName, boolean validate) {
    return () -> {
      Object rawArtifactTemplate =
          ResponseUnwrapper.get(
              getService().getArtifactTemplate(deploymentName, templateName, validate));
      return getObjectMapper().convertValue(rawArtifactTemplate, ArtifactTemplate.class);
    };
  }

  public static Supplier<Void> addArtifactTemplate(
      String deploymentName, boolean validate, ArtifactTemplate template) {
    return () -> {
      ResponseUnwrapper.get(getService().addArtifactTemplate(deploymentName, validate, template));
      return null;
    };
  }

  public static Supplier<Void> setArtifactTemplate(
      String deploymentName, String templateName, boolean validate, ArtifactTemplate template) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setArtifactTemplate(deploymentName, templateName, validate, template));
      return null;
    };
  }

  public static Supplier<Void> deleteArtifactTemplate(
      String deploymentName, String templateName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(
          getService().deleteArtifactTemplate(deploymentName, templateName, validate));
      return null;
    };
  }

  public static Supplier<List<Plugin>> getPlugins(String deploymentName, boolean validate) {
    return () -> {
      Object rawPlugin = ResponseUnwrapper.get(getService().getPlugins(deploymentName, validate));
      return getObjectMapper().convertValue(rawPlugin, new TypeReference<List<Plugin>>() {});
    };
  }

  public static Supplier<Plugin> getPlugin(
      String deploymentName, String pluginName, boolean validate) {
    return () -> {
      Object rawPlugin =
          ResponseUnwrapper.get(getService().getPlugin(deploymentName, pluginName, validate));
      return getObjectMapper().convertValue(rawPlugin, Plugin.class);
    };
  }

  public static Supplier<Void> addPlugin(String deploymentName, boolean validate, Plugin plugin) {
    return () -> {
      ResponseUnwrapper.get(getService().addPlugin(deploymentName, validate, plugin));
      return null;
    };
  }

  public static Supplier<Void> setPlugin(
      String deploymentName, String pluginName, boolean validate, Plugin plugin) {
    return () -> {
      ResponseUnwrapper.get(getService().setPlugin(deploymentName, pluginName, validate, plugin));
      return null;
    };
  }

  public static Supplier<Void> deletePlugin(
      String deploymentName, String pluginName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(getService().deletePlugin(deploymentName, pluginName, validate));
      return null;
    };
  }

  public static Supplier<Void> setPluginEnableDisable(
      String deploymentName, boolean validate, boolean enable) {
    return () -> {
      ResponseUnwrapper.get(getService().setPluginsEnabled(deploymentName, validate, enable));
      return null;
    };
  }

  public static Supplier<Void> setPluginDownloadingEnableDisable(
      String deploymentName, boolean validate, boolean enable) {
    return () -> {
      ResponseUnwrapper.get(
          getService().setPluginsDownloadingEnabled(deploymentName, validate, enable));
      return null;
    };
  }

  public static Supplier<Map<String, PluginRepository>> getPluginRepositories(
      String deploymentName, boolean validate) {
    return () -> {
      Object rawPlugin =
          ResponseUnwrapper.get(getService().getPluginRepositories(deploymentName, validate));
      return getObjectMapper()
          .convertValue(rawPlugin, new TypeReference<Map<String, PluginRepository>>() {});
    };
  }

  public static Supplier<Void> addPluginRepository(
      String deploymentName, boolean validate, PluginRepository pluginRepository) {
    return () -> {
      ResponseUnwrapper.get(
          getService().addPluginRepository(deploymentName, validate, pluginRepository));
      return null;
    };
  }

  public static Supplier<PluginRepository> getPluginRepository(
      String deploymentName, String pluginRepositoryName, boolean validate) {
    return () -> {
      Object rawPluginRepository =
          ResponseUnwrapper.get(
              getService().getPluginRepository(deploymentName, pluginRepositoryName, validate));
      return getObjectMapper().convertValue(rawPluginRepository, PluginRepository.class);
    };
  }

  public static Supplier<Void> setPluginRepository(
      String deploymentName,
      String pluginRepositoryName,
      boolean validate,
      PluginRepository pluginRepository) {
    return () -> {
      ResponseUnwrapper.get(
          getService()
              .setPluginRepository(
                  deploymentName, pluginRepositoryName, validate, pluginRepository));
      return null;
    };
  }

  public static Supplier<Void> deletePluginRepository(
      String deploymentName, String pluginRepositoryName, boolean validate) {
    return () -> {
      ResponseUnwrapper.get(
          getService().deletePluginRepository(deploymentName, pluginRepositoryName, validate));
      return null;
    };
  }

  public static Supplier<Telemetry> getTelemetry(String deploymentName, boolean validate) {
    return () -> {
      Object rawTelemetry =
          ResponseUnwrapper.get(getService().getTelemetry(deploymentName, validate));
      return getObjectMapper().convertValue(rawTelemetry, new TypeReference<Telemetry>() {});
    };
  }

  public static Supplier<Void> setTelemetryEnableDisable(
      String deploymentName, boolean validate, boolean enable) {
    return () -> {
      ResponseUnwrapper.get(getService().setTelemetryEnabled(deploymentName, validate, enable));
      return null;
    };
  }

  public static Supplier<Void> setTelemetry(
      String deploymentName, boolean validate, Telemetry telemetry) {
    return () -> {
      ResponseUnwrapper.get(getService().setTelemetry(deploymentName, validate, telemetry));
      return null;
    };
  }

  private static DaemonService service;
  private static ObjectMapper objectMapper;

  private static DaemonService createService(boolean log) {
    return new RestAdapter.Builder()
        .setEndpoint(GlobalOptions.getGlobalOptions().getDaemonEndpoint())
        .setClient(new OkClient())
        .setConverter(new JacksonConverter(getObjectMapper()))
        .setLogLevel(log ? RestAdapter.LogLevel.FULL : RestAdapter.LogLevel.NONE)
        .build()
        .create(DaemonService.class);
  }
}
