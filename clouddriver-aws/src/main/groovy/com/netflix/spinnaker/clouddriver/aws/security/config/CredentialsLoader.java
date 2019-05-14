/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security.config;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.security.AWSAccountInfoLookup;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.DefaultAWSAccountInfoLookup;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig.Account;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig.Region;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CredentialsLoader<T extends AmazonCredentials> {

  private final AWSCredentialsProvider credentialsProvider;
  private final AWSAccountInfoLookup awsAccountInfoLookup;
  private final Map<String, String> templateValues;
  private final CredentialTranslator<T> credentialTranslator;
  private final ObjectMapper objectMapper;

  public CredentialsLoader(
      AWSCredentialsProvider credentialsProvider,
      AmazonClientProvider amazonClientProvider,
      Class<T> credentialsType) {
    this(
        credentialsProvider,
        amazonClientProvider,
        credentialsType,
        Collections.<String, String>emptyMap());
  }

  public CredentialsLoader(
      AWSCredentialsProvider credentialsProvider,
      AmazonClientProvider amazonClientProvider,
      Class<T> credentialsType,
      Map<String, String> templateValues) {
    this(
        credentialsProvider,
        new DefaultAWSAccountInfoLookup(credentialsProvider, amazonClientProvider),
        credentialsType,
        templateValues);
  }

  public CredentialsLoader(
      AWSCredentialsProvider credentialsProvider,
      AWSAccountInfoLookup awsAccountInfoLookup,
      Class<T> credentialsType) {
    this(
        credentialsProvider,
        awsAccountInfoLookup,
        credentialsType,
        Collections.<String, String>emptyMap());
  }

  public CredentialsLoader(
      AWSCredentialsProvider credentialsProvider,
      AWSAccountInfoLookup awsAccountInfoLookup,
      Class<T> credentialsType,
      Map<String, String> templateValues) {
    this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
    this.awsAccountInfoLookup = awsAccountInfoLookup;
    this.templateValues = templateValues;
    this.objectMapper = new ObjectMapper();
    this.credentialTranslator = findTranslator(credentialsType, this.objectMapper);
  }

  private Lazy<List<Region>> createDefaults(final List<Region> defaults) {
    return new Lazy<>(
        new Lazy.Loader<List<Region>>() {
          @Override
          public List<Region> get() {
            if (defaults == null) {
              return toRegion(awsAccountInfoLookup.listRegions());
            } else {
              List<Region> result = new ArrayList<>(defaults.size());
              List<String> toLookup = new ArrayList<>();
              for (Region def : defaults) {
                if (def.getAvailabilityZones() == null || def.getAvailabilityZones().isEmpty()) {
                  toLookup.add(def.getName());
                } else {
                  result.add(def);
                }
              }
              if (!toLookup.isEmpty()) {
                List<Region> resolved = toRegion(awsAccountInfoLookup.listRegions(toLookup));
                for (Region region : resolved) {
                  Region fromDefault = find(defaults, region.getName());
                  if (fromDefault != null) {
                    region.setPreferredZones(fromDefault.getPreferredZones());
                    region.setDeprecated(fromDefault.getDeprecated());
                  }
                }
                result.addAll(resolved);
              }
              return result;
            }
          }
        });
  }

  private List<Region> initRegions(Lazy<List<Region>> defaults, List<Region> toInit) {
    if (toInit == null) {
      return defaults.get();
    }

    Map<String, Region> toInitByName =
        toInit.stream().collect(Collectors.toMap(Region::getName, Function.identity()));

    List<Region> result = new ArrayList<>(toInit.size());
    List<String> toLookup = new ArrayList<>();
    for (Region r : toInit) {
      if (r.getAvailabilityZones() == null || r.getAvailabilityZones().isEmpty()) {
        toLookup.add(r.getName());
      } else {
        result.add(r);
      }
    }

    for (Iterator<String> lookups = toLookup.iterator(); lookups.hasNext(); ) {
      Region fromDefault = find(defaults.get(), lookups.next());
      if (fromDefault != null) {
        lookups.remove();
        result.add(fromDefault);
      }
    }
    if (!toLookup.isEmpty()) {
      List<Region> resolved = toRegion(awsAccountInfoLookup.listRegions(toLookup));
      for (Region region : resolved) {
        Region src = find(toInit, region.getName());
        if (src == null || src.getPreferredZones() == null) {
          src = find(defaults.get(), region.getName());
        }

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

  public T load(String accountName) throws Throwable {
    CredentialsConfig config = new CredentialsConfig();
    Account account = new Account();
    account.setName(accountName);
    config.setAccounts(Arrays.asList(account));
    List<T> result = load(config);
    if (result.size() != 1) {
      throw new IllegalStateException("failed to create account");
    }
    return result.get(0);
  }

  public List<T> load(CredentialsConfig source) throws Throwable {
    final CredentialsConfig config = objectMapper.convertValue(source, CredentialsConfig.class);

    if (config.getAccounts() == null || config.getAccounts().isEmpty()) {
      return Collections.emptyList();
    }

    Lazy<List<Region>> defaultRegions = createDefaults(config.getDefaultRegions());
    List<T> initializedAccounts = new ArrayList<>(config.getAccounts().size());
    for (Account account : config.getAccounts()) {
      if (account.getAccountId() == null) {
        if (!credentialTranslator.resolveAccountId()) {
          throw new IllegalArgumentException(
              "accountId is required and not resolvable for this credentials type");
        }
        account.setAccountId(awsAccountInfoLookup.findAccountId());
      }

      if (account.getEnvironment() == null) {
        account.setEnvironment(account.getName());
      }

      if (account.getAccountType() == null) {
        account.setAccountType(account.getName());
      }

      account.setRegions(initRegions(defaultRegions, account.getRegions()));
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
          templateFirstNonNull(
              templateContext, account.getEdda(), config.getDefaultEddaTemplate()));
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

      initializedAccounts.add(credentialTranslator.translate(credentialsProvider, account));
    }
    return initializedAccounts.stream()
        .filter(AmazonCredentials::isEnabled)
        .collect(Collectors.toList());
  }

  private static class Lazy<T> {
    public static interface Loader<T> {
      T get();
    }

    private final Loader<T> loader;
    private final AtomicReference<T> ref = new AtomicReference<>();

    public Lazy(Loader<T> loader) {
      this.loader = loader;
    }

    public T get() {
      if (ref.get() == null) {
        ref.set(loader.get());
      }
      return ref.get();
    }
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

  static interface CredentialTranslator<T extends AmazonCredentials> {
    Class<T> getCredentialType();

    boolean resolveAccountId();

    T translate(AWSCredentialsProvider credentialsProvider, Account account) throws Throwable;
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
            credentialType.getConstructor(credentialType, AWSCredentialsProvider.class);
      } catch (NoSuchMethodException nsme) {
        throw new IllegalArgumentException(
            "Class "
                + credentialType
                + " must supply a constructor with "
                + credentialType
                + ", "
                + AWSCredentialsProvider.class
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
    public T translate(AWSCredentialsProvider credentialsProvider, Account account)
        throws Throwable {
      T immutableInstance = objectMapper.convertValue(account, credentialType);
      try {
        return copyConstructor.newInstance(immutableInstance, credentialsProvider);
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
