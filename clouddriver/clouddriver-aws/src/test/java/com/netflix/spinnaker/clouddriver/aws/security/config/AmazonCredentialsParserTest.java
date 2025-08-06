/*
 * Copyright 2021 Salesforce, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AWSAccountInfoLookup;
import com.netflix.spinnaker.clouddriver.aws.security.AWSAccountInfoLookupFactory;
import com.netflix.spinnaker.clouddriver.aws.security.AWSCredentialsProviderFactory;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig.Region;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

class AmazonCredentialsParserTest {

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  public void verifyUseManagingAccountProfileEnabled(@TempDir Path tempDir) throws Throwable {
    // given
    CredentialsConfig.LoadAccounts loadAccounts = new CredentialsConfig.LoadAccounts();
    loadAccounts.setUseManagingAccountProfile(true);
    Region region = new Region(); // an arbitrary region
    region.setName("us-west-2");
    region.setAvailabilityZones(List.of("us-west-2a", "us-west-2b"));
    CredentialsConfig config = new CredentialsConfig();
    config.setLoadAccounts(loadAccounts);
    config.setDefaultRegions(List.of(region));

    String managingAccountProfile = "alternate";
    String alternateRegionName = "alternate-region";

    // Don't provide an account id to verify that it's resolvable
    AccountsConfiguration.Account alternateAccount = new AccountsConfiguration.Account();
    alternateAccount.setName("test-alternate");
    Region configuredRegion = new Region();
    configuredRegion.setName(alternateRegionName);
    List<Region> regions = new ArrayList<>();
    regions.add(configuredRegion);
    alternateAccount.setRegions(regions);
    alternateAccount.setManagingAccountProfile(managingAccountProfile);
    AccountsConfiguration.Account noManagingAccountProfileAccount =
        new AccountsConfiguration.Account();
    noManagingAccountProfileAccount.setName("regular");
    AccountsConfiguration accountsConfig = new AccountsConfiguration();
    accountsConfig.setAccounts(List.of(alternateAccount, noManagingAccountProfileAccount));

    String alternateAccountId = "696969";
    String regularAccountId = "12345";

    AWSCredentialsProvider provider = mock(AWSCredentialsProvider.class);
    AmazonClientProvider amazonClientProvider = mock(AmazonClientProvider.class);
    AWSAccountInfoLookup lookup = mock(AWSAccountInfoLookup.class);
    TestAWSAccountInfoLookupFactory lookupFactory =
        new TestAWSAccountInfoLookupFactory(managingAccountProfile, alternateAccountId);
    TestAWSCredentialsProviderFactory credentialsProviderFactory =
        new TestAWSCredentialsProviderFactory(managingAccountProfile);

    // Set up the AWSAccountInfoLookupFactory for the alternate
    // managingAccountProfile to return a region when asked.  Leave out
    // availability zone information so the credentials loader queries for it.
    AmazonCredentials.AWSRegion alternateRegion =
        new AmazonCredentials.AWSRegion(alternateRegionName, List.of("az-1", "az-2"));

    // Expect alternateRegionName as the only list element passed to listRegions
    // since that's the only region configured in the that account.
    when(lookupFactory
            .getAWSAccountInfoLookup(managingAccountProfile)
            .listRegions(List.of(alternateRegionName)))
        .thenReturn(List.of(alternateRegion));

    AmazonCredentialsParser<AccountsConfiguration.Account, NetflixAmazonCredentials> ci =
        new AmazonCredentialsParser<>(
            provider,
            amazonClientProvider,
            lookup,
            lookupFactory,
            credentialsProviderFactory,
            NetflixAmazonCredentials.class,
            config,
            accountsConfig);

    when(lookup.findAccountId()).thenReturn(regularAccountId);

    // when
    List<NetflixAmazonCredentials> creds = ci.load(config);

    // then
    assertThat(creds).hasSize(2);

    // Assume the credentials here are in order of the accounts provided
    AmazonCredentials alternateCredentials = creds.get(0);
    assertThat(alternateCredentials.getName()).isEqualTo("test-alternate");
    assertThat(alternateCredentials.getAccountId()).isEqualTo(alternateAccountId);

    // Make sure the credentials provider isn't the standalone mock we
    // provided...Instead, make sure it's one that we constructed internally.
    assertThat(alternateCredentials.getCredentialsProvider()).isNotEqualTo(provider);
    assertThat(alternateCredentials.getCredentialsProvider())
        .isEqualTo(credentialsProviderFactory.getAWSCredentialsProvider(managingAccountProfile));

    // Make sure findAccountId was called on the mock from our factory, and
    // listRegions as well
    verify(lookupFactory.getAWSAccountInfoLookup(managingAccountProfile)).findAccountId();
    verify(lookupFactory.getAWSAccountInfoLookup(managingAccountProfile))
        .listRegions(List.of(alternateRegionName));
    verifyNoMoreInteractions(lookupFactory.getAWSAccountInfoLookup(managingAccountProfile));

    AmazonCredentials noManagingAccountProfileCredentials = creds.get(1);
    assertThat(noManagingAccountProfileCredentials.getName()).isEqualTo("regular");
    assertThat(noManagingAccountProfileCredentials.getAccountId()).isEqualTo(regularAccountId);

    // Make sure the credentials provider here IS the standalone mock we
    // provided
    assertThat(noManagingAccountProfileCredentials.getCredentialsProvider()).isEqualTo(provider);

    // Make sure findAccountId was called on the standalone lookup (for the
    // regular account).
    verify(lookup).findAccountId();

    // With a default region that specifies availability zones, expect no
    // listRegions call by the standalone lookup for default region processing
    // even though there's at least one account that doesn't specify a
    // managingAccountProfile.
    verify(lookup, never()).listRegions(List.of());

    verifyNoMoreInteractions(lookup);
  }

  @Test
  public void verifyUseManagingAccountProfileDisabled() throws Throwable {
    // given
    CredentialsConfig.LoadAccounts loadAccounts = new CredentialsConfig.LoadAccounts();
    loadAccounts.setUseManagingAccountProfile(false);
    CredentialsConfig config = new CredentialsConfig();
    config.setLoadAccounts(loadAccounts);

    String managingAccountProfile = "alternate";
    // Don't provide an account id to verify that it's resolvable
    AccountsConfiguration.Account account = new AccountsConfiguration.Account();
    account.setName("test");

    // Specify a managingAccountProfile, so we can verify it's not used because
    // the useManagingAccountProfile flag is false.
    account.setManagingAccountProfile(managingAccountProfile);
    AccountsConfiguration accountsConfig = new AccountsConfiguration();
    accountsConfig.setAccounts(List.of(account));

    String accountId = "696969";

    AWSCredentialsProvider provider = mock(AWSCredentialsProvider.class);
    AmazonClientProvider amazonClientProvider = mock(AmazonClientProvider.class);
    AWSAccountInfoLookup lookup = mock(AWSAccountInfoLookup.class);
    AWSAccountInfoLookupFactory lookupFactory = spy(AWSAccountInfoLookupFactory.class);
    AWSCredentialsProviderFactory credentialsProviderFactory =
        spy(AWSCredentialsProviderFactory.class);
    AmazonCredentialsParser<AccountsConfiguration.Account, NetflixAmazonCredentials> ci =
        new AmazonCredentialsParser<>(
            provider,
            amazonClientProvider,
            lookup,
            lookupFactory,
            credentialsProviderFactory,
            NetflixAmazonCredentials.class,
            config,
            accountsConfig);

    doReturn(accountId).when(lookup).findAccountId();
    doReturn(List.of()).when(lookup).listRegions(List.of());

    // when
    List<NetflixAmazonCredentials> creds = ci.load(config);

    // then
    verify(lookup).findAccountId();
    verify(lookup).listRegions();
    verifyNoMoreInteractions(lookup);

    assertThat(creds).hasSize(1);
    AmazonCredentials credentials = creds.get(0);

    // Make sure the credentials provider is the "standalone" mock we provided,
    // not one from the factory.
    assertThat(credentials.getCredentialsProvider()).isEqualTo(provider);
    assertThat(credentials.getAccountId()).isEqualTo(accountId);

    // And that the standalone lookup object was used to lookup regions
    verify(lookup).listRegions();

    // And make sure our lookup factory and credentials factory never got used
    verifyNoInteractions(lookupFactory);
  }

  @NonnullByDefault
  static class TestAWSAccountInfoLookupFactory implements AWSAccountInfoLookupFactory {

    private final Map<String, AWSAccountInfoLookup> awsAccountInfoLookupMap = new HashMap<>();

    TestAWSAccountInfoLookupFactory(String profileName, String accountId, String... accountIds) {
      AWSAccountInfoLookup awsAccountInfoLookup = mock(AWSAccountInfoLookup.class);
      when(awsAccountInfoLookup.findAccountId()).thenReturn(accountId, accountIds);
      awsAccountInfoLookupMap.put(profileName, awsAccountInfoLookup);
    }

    @Override
    public AWSAccountInfoLookup makeAWSAccountInfoLookup(
        String profileName,
        AWSCredentialsProvider credentialsProvider,
        AmazonClientProvider amazonClientProvider) {
      AWSAccountInfoLookup awsAccountInfoLookup = getAWSAccountInfoLookup(profileName);
      assertNotNull(awsAccountInfoLookup);
      return awsAccountInfoLookup;
    }

    public AWSAccountInfoLookup getAWSAccountInfoLookup(String profileName) {
      return awsAccountInfoLookupMap.get(profileName);
    }
  }

  @NonnullByDefault
  static class TestAWSCredentialsProviderFactory implements AWSCredentialsProviderFactory {

    private final Map<String, AWSCredentialsProvider> awsCredentialsProviderMap = new HashMap<>();

    TestAWSCredentialsProviderFactory(String profileName) {
      AWSCredentialsProvider awsCredentialsProvider = mock(AWSCredentialsProvider.class);
      awsCredentialsProviderMap.put(profileName, awsCredentialsProvider);
    }

    @Override
    public AWSCredentialsProvider makeAWSCredentialsProvider(String profileName) {
      AWSCredentialsProvider awsCredentialsProvider = getAWSCredentialsProvider(profileName);
      assertNotNull(awsCredentialsProvider);
      return awsCredentialsProvider;
    }

    public AWSCredentialsProvider getAWSCredentialsProvider(String profileName) {
      return awsCredentialsProviderMap.get(profileName);
    }
  }
}
