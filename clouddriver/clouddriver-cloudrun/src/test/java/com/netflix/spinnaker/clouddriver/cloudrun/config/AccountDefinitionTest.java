package com.netflix.spinnaker.clouddriver.cloudrun.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class AccountDefinitionTest {
  @Test
  public void testCredentialsEquality() {
    CloudrunConfigurationProperties.ManagedAccount account1 =
        new CloudrunConfigurationProperties.ManagedAccount()
            .setServiceAccountEmail("email@example.com");
    account1.setName("cloudrun-1");
    CloudrunConfigurationProperties.ManagedAccount account2 =
        new CloudrunConfigurationProperties.ManagedAccount()
            .setServiceAccountEmail("email@example.com");
    account2.setName("cloudrun-2");

    assertThat(account1).isNotEqualTo(account2);

    // Check name is part of the comparison
    account2.setName("cloudrun-1");
    assertThat(account1).isEqualTo(account1);
  }
}
