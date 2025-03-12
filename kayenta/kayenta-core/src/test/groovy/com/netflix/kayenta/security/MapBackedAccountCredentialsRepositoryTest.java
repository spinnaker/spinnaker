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

import static com.netflix.kayenta.security.AccountCredentials.Type.CONFIGURATION_STORE;
import static com.netflix.kayenta.security.AccountCredentials.Type.METRICS_STORE;
import static com.netflix.kayenta.security.AccountCredentials.Type.OBJECT_STORE;
import static com.netflix.kayenta.security.AccountCredentials.Type.REMOTE_JUDGE;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

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

  @Test
  public void getAllAccountsOfType_returnsAccountsOfSpecificTypeOnly() {
    AccountCredentials account1 = namedAccount("account1", METRICS_STORE, OBJECT_STORE);
    AccountCredentials account2 =
        namedAccount("account2", METRICS_STORE, OBJECT_STORE, CONFIGURATION_STORE, REMOTE_JUDGE);
    AccountCredentials account3 = namedAccount("account3");
    repository.save("account1", account1);
    repository.save("account2", account2);
    repository.save("account3", account3);

    assertThat(repository.getAllOf(METRICS_STORE)).containsOnly(account1, account2);
    assertThat(repository.getAllOf(OBJECT_STORE)).containsOnly(account1, account2);
    assertThat(repository.getAllOf(CONFIGURATION_STORE)).containsOnly(account2);
    assertThat(repository.getAllOf(REMOTE_JUDGE)).containsOnly(account2);
  }

  @Test
  public void getRequiredOneBy_returnsActualAccountByName() {
    AccountCredentials account1 = namedAccount("account1", METRICS_STORE);
    repository.save("account1", account1);

    assertThat(repository.getRequiredOneBy("account1", METRICS_STORE)).isEqualTo(account1);
  }

  @Test
  public void getRequiredOneBy_returnsFirstAvailableAccountByTypeIfNameIsNotProvided() {
    AccountCredentials account1 = namedAccount("account1", METRICS_STORE, OBJECT_STORE);
    AccountCredentials account2 = namedAccount("account2", METRICS_STORE, OBJECT_STORE);
    AccountCredentials account3 = namedAccount("account3", METRICS_STORE, OBJECT_STORE);
    repository.save("account1", account1);
    repository.save("account2", account2);
    repository.save("account3", account3);

    assertThat(repository.getRequiredOneBy(null, METRICS_STORE)).isIn(account1, account2, account3);
    assertThat(repository.getRequiredOneBy("", METRICS_STORE)).isIn(account1, account2, account3);
  }

  @Test
  public void deleteById_deletesTheAccount() {

    AccountCredentials account1 = namedAccount("account1");
    AccountCredentials account2 = namedAccount("account2");
    AccountCredentials account3 = namedAccount("account3");
    this.repository.save("account1", account1);
    this.repository.save("account2", account2);
    this.repository.save("account3", account3);
    assertThat(this.repository.getAll())
        .isNotEmpty()
        .hasSize(3)
        .isEqualTo(Set.of(account1, account2, account3));

    this.repository.deleteById("account1");

    assertThat(this.repository.getAll())
        .isNotEmpty()
        .hasSize(2)
        .isEqualTo(Set.of(account2, account3));

    assertThat(this.repository.getOne("account")).isEmpty();
  }

  @Test
  public void getRequiredOneBy_throwsExceptionIfCannotResolveAccount() {
    assertThatThrownBy(() -> repository.getRequiredOneBy(null, METRICS_STORE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private AccountCredentials namedAccount(String name, AccountCredentials.Type... types) {
    AccountCredentials account = mock(AccountCredentials.class);
    when(account.getName()).thenReturn(name);
    when(account.getSupportedTypes()).thenReturn(Arrays.asList(types));
    return account;
  }
}
