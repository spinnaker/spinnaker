/*
 * * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.security;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import org.junit.Test;

public class EcsAccountBuilderTest {

  @Test
  public void shouldBuildAccount() {
    // Given
    String accountName = "ecs-test-account";
    String accountType = "ecs";

    NetflixAmazonCredentials netflixAmazonCredentials = mock(NetflixAmazonCredentials.class);
    when(netflixAmazonCredentials.getPermissions()).thenReturn(mock(Permissions.class));
    when(netflixAmazonCredentials.getAccountId()).thenReturn("id-1234567890");

    // When
    CredentialsConfig.Account account =
        EcsAccountBuilder.build(netflixAmazonCredentials, accountName, accountType);

    // Then
    assertTrue(
        "The new account should not be of the same type as the old account ("
            + netflixAmazonCredentials.getAccountType()
            + ").",
        !account.getAccountType().equals(netflixAmazonCredentials.getAccountType()));

    assertTrue(
        "The new account should not have the same name as the old account ("
            + netflixAmazonCredentials.getName()
            + ").",
        !account.getName().equals(netflixAmazonCredentials.getName()));

    assertTrue(
        "The new account should have the same account ID as the old one ("
            + netflixAmazonCredentials.getAccountId()
            + ") but has "
            + account.getAccountId()
            + " as the ID.",
        account.getAccountId().equals(netflixAmazonCredentials.getAccountId()));
  }
}
