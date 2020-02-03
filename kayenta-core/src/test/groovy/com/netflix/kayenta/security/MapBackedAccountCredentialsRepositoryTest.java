/*
 * Copyright 2020 Playtika.
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
 */

package com.netflix.kayenta.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

public class MapBackedAccountCredentialsRepositoryTest {

  AccountCredentialsRepository repository = new MapBackedAccountCredentialsRepository();

  @Test
  public void getOne_returnsEmptyIfAccountNotPresent() {
    assertThat(repository.getOne("account")).isEmpty();
  }

  @Test
  public void getOne_returnsPresentAccount() {
    AccountCredentials account = namedAccount("account1");
    repository.save("account1", account);

    assertThat(repository.getOne("account1")).hasValue(account);
  }

  @Test
  public void getRequiredOne_throwsExceptionIfAccountNotPresent() {
    assertThatThrownBy(() -> repository.getRequiredOne("account"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unable to resolve account account.");
  }

  @Test
  public void getRequiredOne_returnsPresentAccount() {
    AccountCredentials account = namedAccount("account1");
    repository.save("account1", account);

    AccountCredentials actual = repository.getRequiredOne("account1");
    assertThat(actual).isEqualTo(account);
  }

  private AccountCredentials namedAccount(String name) {
    AccountCredentials account = mock(AccountCredentials.class);
    when(account.getName()).thenReturn(name);
    return account;
  }
}
