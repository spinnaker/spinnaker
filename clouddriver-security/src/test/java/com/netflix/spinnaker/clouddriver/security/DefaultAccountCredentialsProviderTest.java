/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.clouddriver.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.AdditionalMatchers;

@RunWith(JUnitPlatform.class)
public class DefaultAccountCredentialsProviderTest {

  @Test
  void testSimpleCredentialsProvider() {
    String NAME = "accountName";
    AccountCredentialsRepository repo = mock(AccountCredentialsRepository.class);
    AccountCredentials cred1 = mock(AccountCredentials.class);
    HashSet<AccountCredentials> set = new HashSet<>(ImmutableList.of(cred1));
    when(repo.getAll()).thenAnswer(invocation -> set);
    when(repo.getOne(NAME)).thenReturn(cred1);

    DefaultAccountCredentialsProvider provider = new DefaultAccountCredentialsProvider(repo);
    assertThat(provider.getAll()).hasSize(1);
    assertThat(provider.getCredentials(NAME)).isEqualTo(cred1);
  }

  @Test
  void testCompositeCredentialsProvider() {
    String NAME1 = "account1";
    String NAME2 = "account2";
    String NAME3 = "account3";
    AccountCredentialsRepository repo = mock(AccountCredentialsRepository.class);
    AccountCredentials cred1 = mock(AccountCredentials.class);
    HashSet<AccountCredentials> set = new HashSet<>(ImmutableList.of(cred1));
    when(repo.getAll()).thenAnswer(invocation -> set);
    when(repo.getOne(NAME1)).thenReturn(cred1);
    when(repo.getOne(AdditionalMatchers.not(eq(NAME1)))).thenReturn(null);

    CompositeCredentialsRepository compositeRepo = mock(CompositeCredentialsRepository.class);
    AccountCredentials cred2 = mock(AccountCredentials.class);
    when(compositeRepo.getAllCredentials()).thenReturn(ImmutableList.of(cred2));
    when(compositeRepo.getFirstCredentialsWithName(NAME2)).thenReturn(cred2);
    when(compositeRepo.getFirstCredentialsWithName(AdditionalMatchers.not(eq(NAME2))))
        .thenReturn(null);

    DefaultAccountCredentialsProvider provider =
        new DefaultAccountCredentialsProvider(repo, compositeRepo);
    assertThat(provider.getAll()).hasSize(2);
    assertThat(provider.getCredentials(NAME1)).isEqualTo(cred1);
    assertThat(provider.getCredentials(NAME2)).isEqualTo(cred2);
    assertThat(provider.getCredentials(NAME3)).isNull();
  }
}
