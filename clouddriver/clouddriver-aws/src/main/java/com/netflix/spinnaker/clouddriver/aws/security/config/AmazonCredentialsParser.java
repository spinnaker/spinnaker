/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.aws.security.config;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.util.CollectionUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.netflix.spinnaker.clouddriver.aws.AwsConfigurationProperties;
import com.netflix.spinnaker.clouddriver.aws.security.AWSAccountInfoLookup;
import com.netflix.spinnaker.clouddriver.aws.security.AWSAccountInfoLookupFactory;
import com.netflix.spinnaker.clouddriver.aws.security.AWSCredentialsProviderFactory;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.AccountsConfiguration.Account;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig.Region;
import com.netflix.spinnaker.credentials.definition.CredentialsParser;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class AmazonCredentialsParser<
        U extends AccountsConfiguration.Account, V extends NetflixAmazonCredentials>
    implements CredentialsParser<U, V> {

  private final AmazonClientProvider amazonClientProvider;

  private final AWSCredentialsProvider credentialsProvider;
  private final AWSAccountInfoLookup awsAccountInfoLookup;

  /** Used with multiple managing accounts to lookup aws account info */
  private final AWSAccountInfoLookupFactory awsAccountInfoLookupFactory;

  /** Used with multiple managing accounts to provide aws account credentials */
  private final AWSCredentialsProviderFactory awsCredentialsProviderFactory;

  /** The key is a profile name, and the value is the provider for that profile */
  private final Map<String, AWSCredentialsProvider> credentialsProviderMap;

  /** The key is a profile name, and the value is account info lookup object for that profile. */
  private final Map<String, AWSAccountInfoLookup> awsAccountInfoLookupMap;

  private final Map<String, String> templateValues;
  private final CredentialTranslator<V> credentialTranslator;
  private final ObjectMapper objectMapper;
  private final CredentialsConfig credentialsConfig;
  private final AccountsConfiguration accountsConfig;
  // this is used to cache all the regions found while parsing the accounts. This helps in
  // reducing the number of API calls made since known regions are already cached.
  private final ConcurrentMap<String, Region> regionCache;
  private final RetryRegistry retryRegistry;
  private final AwsConfigurationProperties awsConfigurationProperties;
  private List<String> defaultRegionNames;

  // this is a key used in the regions cache to indicate that default regions have been
  // processed
  private static final String DEFAULT_REGIONS_PROCESSED_KEY = "default_regions_processed";

  public AmazonCredentialsParser(
      AWSCredentialsProvider credentialsProvider,
      AmazonClientProvider amazonClientProvider,
      AWSAccountInfoLookup awsAccountInfoLookup,
      AWSAccountInfoLookupFactory awsAccountInfoLookupFactory,
      AWSCredentialsProviderFactory awsCredentialsProviderFactory,
      Class<V> credentialsType,
      CredentialsConfig credentialsConfig,
      AccountsConfiguration accountsConfig,
      AwsConfigurationProperties awsConfigurationProperties) {
    this.amazonClientProvider =
        Objects.requireNonNull(amazonClientProvider, "amazonClientProvider");
    this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
    this.awsAccountInfoLookup = awsAccountInfoLookup;
    this.awsAccountInfoLookupFactory =
        Objects.requireNonNull(awsAccountInfoLookupFactory, "awsAccountInfoLookupFactory");
    this.awsCredentialsProviderFactory =
        Objects.requireNonNull(awsCredentialsProviderFactory, "awsCredentialsProviderFactory");
    this.templateValues = Collections.emptyMap();
    this.objectMapper = new ObjectMapper();
    this.credentialTranslator = findTranslator(credentialsType, this.objectMapper);
    this.credentialsConfig = credentialsConfig;

    // Only used when CredentialsConfig.getLoadAccounts().isUseManagingAccountProfile() is true
    this.credentialsProviderMap = Maps.newConcurrentMap();
    this.awsAccountInfoLookupMap = Maps.newConcurrentMap();

    this.accountsConfig = accountsConfig;
    this.regionCache = Maps.newConcurrentMap();
    this.defaultRegionNames = new ArrayList<>();

    this.retryRegistry = getRetryRegistry();
    this.awsConfigurationProperties = Objects.requireNonNull(awsConfigurationProperties);

    // look in the credentials config to find default region names
    if (!CollectionUtils.isNullOrEmpty(credentialsConfig.getDefaultRegions())) {
      // With multiple managing accounts, is there more than one list of default
      // regions?  It's possible to add another property to add profile-specific
      // defaults, like:
      //
      // private Map<String, Region> defaultRegionsPerProfile;
      //
      // but until then assume defaultRegions in the config only applies to
      // accounts that don't specify a managing account profile.
      //
      // If managing account profiles are enabled, and every account specifies a
      // managing account profile, we potentially don't need to do this, but leave
      // it in for simplicity and since the region cache is shared, so there may
      // be a benefit to populating it here.
      this.defaultRegionNames =
          credentialsConfig.getDefaultRegions().stream()
              .map(Region::getName)
              .collect(Collectors.toList());
    }
  }

  /**
   * method to initialize the regions specified in an AWS account in the configuration.
   *
   * <p>Live call to get regions from the AWS API will be made if:
   *
   * <p>- An account's region does not have availability zones defined and that region doesn't exist
   * in the region cache.
   */
  private List<Region> initRegions(AWSAccountInfoLookup awsAccountInfoLookup, List<Region> toInit) {
    // initialize regions cache if it hasn't been done already. We do this here and not in
    // toInit.isNullOrEmpty() because we need the default region values if a region in toInit list
    // has no availability zones specified.
    initializeRegionsCacheWithDefaultRegions();

    if (CollectionUtils.isNullOrEmpty(toInit)) {
      return getRegionsFromCache(awsAccountInfoLookup, this.defaultRegionNames);
    }

    Map<String, Region> toInitByName =
        toInit.stream().collect(Collectors.toMap(Region::getName, Function.identity()));

    List<Region> result = new ArrayList<>(toInit.size());
    List<String> toLookup = new ArrayList<>();
    for (Region region : toInit) {
      // only attempt to lookup regions that don't have any availability zones set in the config
      if (CollectionUtils.isNullOrEmpty(region.getAvailabilityZones())) {
        Region fromCache = regionCache.get(region.getName());
        // no need to lookup the region if it already exists in the cache
        if (fromCache != null) {
          result.add(fromCache);
        } else {
          toLookup.add(region.getName());
        }
      } else {
        result.add(region);
      }
    }

    // toLookup now contains the list of regions that we need to fetch from the cache and/or AWS API
    if (!toLookup.isEmpty()) {
      List<Region> resolved = getRegionsFromCache(awsAccountInfoLookup, toLookup);
      for (Region region : resolved) {
        Region src = find(toInit, region.getName());
        if (src != null) {
          region.setPreferredZones(src.getPreferredZones());
        }
      }
      result.addAll(resolved);
    }

    // make a clone of all regions such that modifications apply only to this specific instance (and
    // not global defaults)
    result = result.stream().map(Region::copyOf).collect(Collectors.toList());

    for (Region r : result) {
      Region toInitRegion = toInitByName.get(r.getName());
      if (toInitRegion != null && toInitRegion.getDeprecated() != null) {
        r.setDeprecated(toInitRegion.getDeprecated());
      }
    }

    return result;
  }

  /**
   * method to initialize the regions cache by processing the default regions which may have been
   * specified in the configuration.
   *
   * <p>Live call to get regions from the AWS API will be made if:
   *
   * <p>1. no default regions exist in the config - in this case, it will fetch all AWS regions
   *
   * <p>2. default regions exist in the config but they don't have availability zones defined
   */
  private void initializeRegionsCacheWithDefaultRegions() {
    // synchronized block is added here to handle the multi-threading case where multiple threads
    // may attempt to initialize the regions cache at the same time when it is empty in the
    // beginning. This block will reduce the number of api calls made to look up regions
    // by only allowing one of the threads to do that.
    synchronized (this) {
      if (!regionCache.containsKey(DEFAULT_REGIONS_PROCESSED_KEY)) {
        // if there are no default regions specified, then fetch all the AWS regions.
        if (defaultRegionNames.isEmpty()) {
          log.info("No default regions specified in the configuration. Retrieving all the regions");
          // save all the newly found regions in the cache
          toRegion(this.awsAccountInfoLookup.listRegions())
              .forEach(
                  region -> {
                    log.info("adding region: {} to regions cache", region.getName());
                    regionCache.putIfAbsent(region.getName(), region);
                  });
        } else {
          List<String> toLookup = new ArrayList<>();
          for (Region region : credentialsConfig.getDefaultRegions()) {
            log.info("Found default region: {} in the configuration", region.getName());
            if (region.getAvailabilityZones() != null && !region.getAvailabilityZones().isEmpty()) {
              log.info("Adding default region: {} to the regions cache", region.getName());
              regionCache.put(region.getName(), region);
            } else {
              toLookup.add(region.getName());
            }
          }

          if (!toLookup.isEmpty()) {
            log.info("Fetching default regions: {}", toLookup);
            List<AmazonCredentials.AWSRegion> newRegions =
                awsAccountInfoLookup.listRegions(toLookup);

            // save all the newly found regions in the cache
            toRegion(newRegions)
                .forEach(
                    region -> {
                      log.info("adding default region: {} to the regions cache", region.getName());
                      Region fromDefault =
                          find(credentialsConfig.getDefaultRegions(), region.getName());
                      if (fromDefault != null) {
                        region.setPreferredZones(fromDefault.getPreferredZones());
                        region.setDeprecated(fromDefault.getDeprecated());
                      }
                      regionCache.put(region.getName(), region);
                    });
          }
        }
        // this helps us know that we have processed default regions. The value here doesn't matter.
        regionCache.put(DEFAULT_REGIONS_PROCESSED_KEY, new Region());
      }
    }
  }

  private static Region find(List<Region> src, String name) {
    if (src != null) {
      for (Region r : src) {
        if (r.getName().equals(name)) {
          return r;
        }
      }
    }
    return null;
  }

  private List<Region> getRegionsFromCache(
      AWSAccountInfoLookup awsAccountInfoLookup, final List<String> regionNames) {
    // if no region names are provided, return everything from the cache except the
    // DEFAULT_REGIONS_PROCESSED_KEY
    if (regionNames.isEmpty()) {
      return regionCache.entrySet().stream()
          .filter(entry -> !entry.getKey().equals(DEFAULT_REGIONS_PROCESSED_KEY))
          .map(Map.Entry::getValue)
          .collect(Collectors.toList());
    }

    // determine if any regions are missing from the cache
    List<String> cacheMisses = new ArrayList<>();
    for (String region : regionNames) {
      if (!regionCache.containsKey(region)) {
        cacheMisses.add(region);
      }
    }

    if (!cacheMisses.isEmpty()) {
      List<AmazonCredentials.AWSRegion> newRegions;
      log.info("Regions: {} do not exist in the regions cache", cacheMisses);
      newRegions = awsAccountInfoLookup.listRegions(cacheMisses);
      // save all the newly found regions in the cache
      toRegion(newRegions)
          .forEach(
              region -> {
                log.info("adding region: {} to regions cache", region.getName());
                regionCache.putIfAbsent(region.getName(), region);
              });
    }
    return regionNames.stream().map(regionCache::get).collect(Collectors.toList());
  }

  private static List<Region> toRegion(List<AmazonCredentials.AWSRegion> src) {
    List<Region> result = new ArrayList<>(src.size());
    for (AmazonCredentials.AWSRegion r : src) {
      Region region = new Region();
      region.setName(r.getName());
      region.setAvailabilityZones(new ArrayList<>(r.getAvailabilityZones()));
      region.setPreferredZones(new ArrayList<>(r.getPreferredZones()));
      result.add(region);
    }
    return result;
  }

  // TODO: verify if this is safe to be removed if it is not used anywhere else apart from tests
  public List<V> load(CredentialsConfig source) throws Throwable {
    final CredentialsConfig config = objectMapper.convertValue(source, CredentialsConfig.class);
    if (accountsConfig.getAccounts() == null || accountsConfig.getAccounts().isEmpty()) {
      return Collections.emptyList();
    }
    List<V> initializedAccounts = new ArrayList<>(accountsConfig.getAccounts().size());
    for (Account account : accountsConfig.getAccounts()) {
      initializedAccounts.add(parseAccount(config, account));
    }
    return initializedAccounts.stream()
        .filter(AmazonCredentials::isEnabled)
        .collect(Collectors.toList());
  }

  @Nullable
  @Override
  public V parse(@NotNull U account) {
    try {
      log.info("Parsing aws account: {}", account.getName());
      V a = parseAccount(credentialsConfig, account);
      if (a.isEnabled()) {
        log.info("AWS account: {} is enabled", account.getName());
        return a;
      } else {
        log.info("AWS account: {} is disabled", account.getName());
      }
    } catch (Throwable t) {
      log.warn("Failed to parse aws account: {}. Error: ", account.getName(), t);
    }
    return null;
  }

  private V parseAccount(CredentialsConfig config, Account account) throws Throwable {
    Retry retry = getRetry(retryRegistry, account.getName());
    if (account.getAccountId() == null) {
      if (!credentialTranslator.resolveAccountId()) {
        throw new IllegalArgumentException(
            "accountId is required and not resolvable for this credentials type");
      }

      account.setAccountId(
          Retry.decorateSupplier(retry, getAwsAccountInfoLookup(config, account)::findAccountId)
              .get());
    }

    if (account.getEnvironment() == null) {
      account.setEnvironment(account.getName());
    }

    if (account.getAccountType() == null) {
      account.setAccountType(account.getName());
    }

    log.info("Setting regions for aws account: {}", account.getName());

    AWSAccountInfoLookup awsAccountInfoLookupToUse = getAwsAccountInfoLookup(config, account);
    account.setRegions(
        Retry.decorateSupplier(
                retry, () -> initRegions(awsAccountInfoLookupToUse, account.getRegions()))
            .get());

    account.setDefaultSecurityGroups(
        account.getDefaultSecurityGroups() != null
            ? account.getDefaultSecurityGroups()
            : config.getDefaultSecurityGroups());
    account.setLifecycleHooks(
        account.getLifecycleHooks() != null
            ? account.getLifecycleHooks()
            : config.getDefaultLifecycleHooks());
    account.setEnabled(Optional.ofNullable(account.getEnabled()).orElse(true));

    Map<String, String> templateContext = new HashMap<>(templateValues);
    templateContext.put("name", account.getName());
    templateContext.put("accountId", account.getAccountId());
    templateContext.put("environment", account.getEnvironment());
    templateContext.put("accountType", account.getAccountType());

    account.setDefaultKeyPair(
        templateFirstNonNull(
            templateContext, account.getDefaultKeyPair(), config.getDefaultKeyPairTemplate()));
    account.setEdda(
        templateFirstNonNull(templateContext, account.getEdda(), config.getDefaultEddaTemplate()));
    account.setFront50(
        templateFirstNonNull(
            templateContext, account.getFront50(), config.getDefaultFront50Template()));
    account.setDiscovery(
        templateFirstNonNull(
            templateContext, account.getDiscovery(), config.getDefaultDiscoveryTemplate()));
    account.setAssumeRole(
        templateFirstNonNull(
            templateContext, account.getAssumeRole(), config.getDefaultAssumeRole()));
    account.setSessionName(
        templateFirstNonNull(
            templateContext, account.getSessionName(), config.getDefaultSessionName()));
    account.setSessionDurationSeconds(
        account.getSessionDurationSeconds() != null
            ? account.getSessionDurationSeconds()
            : config.getDefaultSessionDurationSeconds());
    account.setBastionHost(
        templateFirstNonNull(
            templateContext, account.getBastionHost(), config.getDefaultBastionHostTemplate()));

    if (account.getLifecycleHooks() != null) {
      for (CredentialsConfig.LifecycleHook lifecycleHook : account.getLifecycleHooks()) {
        lifecycleHook.setRoleARN(
            templateFirstNonNull(
                templateContext,
                lifecycleHook.getRoleARN(),
                config.getDefaultLifecycleHookRoleARNTemplate()));
        lifecycleHook.setNotificationTargetARN(
            templateFirstNonNull(
                templateContext,
                lifecycleHook.getNotificationTargetARN(),
                config.getDefaultLifecycleHookNotificationTargetARNTemplate()));
      }
    }
    return credentialTranslator.translate(
        getCredentialsProvider(config, account), account, awsConfigurationProperties);
  }

  /**
   * Accessor for the AWSAccountInfoLookup object for a particular account
   *
   * @param credentialsConfig standalone credentials configuration
   * @param account access the credentials provider for this account
   * @return the AWSAccountInfoLookup object for account
   */
  private AWSAccountInfoLookup getAwsAccountInfoLookup(
      CredentialsConfig credentialsConfig, Account account) {
    // If the useManagingAccountProfile feature is disabled, return the standalone object.
    if (!credentialsConfig.getLoadAccounts().isUseManagingAccountProfile()) {
      log.debug("useManagingAccountProfile is disabled, using standalone AWSAccountInfoLookup");
      return awsAccountInfoLookup;
    }

    // If the useManagingAccountProfile feature is enabled, but the account
    // doesn't specify a managing account profile to use, return the standalone
    // object.
    if (Strings.isNullOrEmpty(account.getManagingAccountProfile())) {
      log.debug(
          "useManagingAccountProfile is enabled but account {} doesn't specify a managing "
              + "account profile, using standalone AWSAccountInfoLookup",
          account.getName());
      return awsAccountInfoLookup;
    }

    // If the account does specify a managing account profile, construct a
    // account info lookup object if we haven't already and use it.
    log.debug(
        "getAwsAccountInfoLookup: account {} specifies a managing account profile: '{}'",
        account.getName(),
        account.getManagingAccountProfile());
    return awsAccountInfoLookupMap.computeIfAbsent(
        account.getManagingAccountProfile(),
        k -> {
          log.info(
              "constructing AWSAccountInfoLookup for profile '{}'",
              account.getManagingAccountProfile());
          return awsAccountInfoLookupFactory.makeAWSAccountInfoLookup(
              account.getManagingAccountProfile(),
              getCredentialsProvider(credentialsConfig, account),
              amazonClientProvider);
        });
  }

  /**
   * Accessor for the credentials provider for a particular account
   *
   * @param credentialsConfig standalone credentials configuration
   * @param account access the credentials provider for this account
   * @return the credentials provider for account
   */
  private AWSCredentialsProvider getCredentialsProvider(
      CredentialsConfig credentialsConfig, Account account) {
    // If the useManagingAccountProfile feature is disabled, return the standalone credentials
    // provider.
    if (!credentialsConfig.getLoadAccounts().isUseManagingAccountProfile()) {
      log.debug("useManagingAccountProfile is disabled, using standalone credentials provider");
      return credentialsProvider;
    }

    // If the useManagingAccountProfile feature is enabled, but the account
    // doesn't specify a managing account profile to use, return the standalone
    // credentials provider.
    if (Strings.isNullOrEmpty(account.getManagingAccountProfile())) {
      log.debug(
          "useManagingAccountProfile is enabled but account {} doesn't specify a managing "
              + "account profile, using standalone credentials provider",
          account.getName());
      return credentialsProvider;
    }

    // If the account does specify a managing account profile, construct a
    // provider if we haven't already and use it.
    log.debug(
        "account {} specifies a managing account profile: '{}'",
        account.getName(),
        account.getManagingAccountProfile());
    return credentialsProviderMap.computeIfAbsent(
        account.getManagingAccountProfile(),
        k -> {
          log.info(
              "constructing ProfileCredentialsProvider for profile '{}'",
              account.getManagingAccountProfile());
          return awsCredentialsProviderFactory.makeAWSCredentialsProvider(
              account.getManagingAccountProfile());
        });
  }

  /** Build a RetryRegistry object based on credentials configuration */
  private RetryRegistry getRetryRegistry() {
    log.info("initializing retry registry");
    RetryConfig retryConfig;
    if (credentialsConfig.getLoadAccounts().isExponentialBackoff()) {
      retryConfig =
          RetryConfig.custom()
              .maxAttempts(credentialsConfig.getLoadAccounts().getMaxRetries())
              .intervalFunction(
                  IntervalFunction.ofExponentialBackoff(
                      Duration.ofMillis(
                          credentialsConfig.getLoadAccounts().getExponentialBackOffIntervalMs()),
                      credentialsConfig.getLoadAccounts().getExponentialBackoffMultiplier()))
              .build();
    } else {
      retryConfig =
          RetryConfig.custom()
              .maxAttempts(credentialsConfig.getLoadAccounts().getMaxRetries())
              .waitDuration(Duration.ofMillis(credentialsConfig.getLoadAccounts().getBackOffInMs()))
              .build();
    }

    return RetryRegistry.of(retryConfig);
  }

  /** Build a Retry object with a given identifier */
  private Retry getRetry(RetryRegistry retryRegistry, String identifier) {
    Retry retry = retryRegistry.retry(identifier);
    Retry.EventPublisher publisher = retry.getEventPublisher();
    publisher.onRetry(event -> log.info(event.toString()));
    publisher.onSuccess(event -> log.info(event.toString()));
    return retry;
  }

  private static String templateFirstNonNull(Map<String, String> substitutions, String... values) {
    for (String value : values) {
      if (value != null) {
        return StringTemplater.render(value, substitutions);
      }
    }
    return null;
  }

  static <T extends AmazonCredentials> CredentialTranslator<T> findTranslator(
      Class<T> credentialsType, ObjectMapper objectMapper) {
    return new CopyConstructorTranslator<>(objectMapper, credentialsType);
  }

  interface CredentialTranslator<T extends AmazonCredentials> {
    Class<T> getCredentialType();

    boolean resolveAccountId();

    T translate(
        AWSCredentialsProvider credentialsProvider,
        Account account,
        AwsConfigurationProperties awsConfigurationProperties)
        throws Throwable;
  }

  static class CopyConstructorTranslator<T extends AmazonCredentials>
      implements CredentialTranslator<T> {

    private final ObjectMapper objectMapper;
    private final Class<T> credentialType;
    private final Constructor<T> copyConstructor;

    public CopyConstructorTranslator(ObjectMapper objectMapper, Class<T> credentialType) {
      this.objectMapper = objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      this.credentialType = credentialType;
      try {
        copyConstructor =
            credentialType.getConstructor(
                credentialType, AWSCredentialsProvider.class, AwsConfigurationProperties.class);
      } catch (NoSuchMethodException nsme) {
        throw new IllegalArgumentException(
            "Class "
                + credentialType
                + " must supply a constructor with "
                + credentialType
                + ", "
                + AWSCredentialsProvider.class
                + ", "
                + AwsConfigurationProperties.class
                + " args.");
      }
    }

    @Override
    public Class<T> getCredentialType() {
      return credentialType;
    }

    @Override
    public boolean resolveAccountId() {
      try {
        credentialType.getMethod("getAssumeRole");
        return false;
      } catch (NoSuchMethodException nsme) {
        return true;
      }
    }

    @Override
    public T translate(
        AWSCredentialsProvider credentialsProvider,
        Account account,
        AwsConfigurationProperties awsConfigurationProperties)
        throws Throwable {
      T immutableInstance = objectMapper.convertValue(account, credentialType);
      try {
        return copyConstructor.newInstance(
            immutableInstance, credentialsProvider, awsConfigurationProperties);
      } catch (InvocationTargetException ite) {
        throw ite.getTargetException();
      }
    }
  }

  static class StringTemplater {
    public static String render(String template, Map<String, String> substitutions) {
      String base = template;
      int iterations = 0;
      boolean changed = true;
      while (changed && iterations < 10) {
        iterations++;
        String previous = base;
        for (Map.Entry<String, String> substitution : substitutions.entrySet()) {
          base =
              base.replaceAll(
                  Pattern.quote("{{" + substitution.getKey() + "}}"), substitution.getValue());
        }
        changed = !previous.equals(base);
      }
      if (changed) {
        throw new RuntimeException("too many levels of templatery");
      }
      return base;
    }
  }
}
