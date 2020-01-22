/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.gcb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GoogleCloudBuildAccountRepositoryTest {
  @Test
  public void emptyRepository() {
    GoogleCloudBuildAccountRepository repository =
        GoogleCloudBuildAccountRepository.builder().build();

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> repository.getGoogleCloudBuild("missing"));
  }

  @Test
  public void missingAccount() {
    GoogleCloudBuildAccount presentAccount = mock(GoogleCloudBuildAccount.class);
    GoogleCloudBuildAccountRepository repository =
        GoogleCloudBuildAccountRepository.builder()
            .registerAccount("present", presentAccount)
            .build();

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> repository.getGoogleCloudBuild("missing"));
  }

  @Test
  public void presentAccount() {
    GoogleCloudBuildAccount presentAccount = mock(GoogleCloudBuildAccount.class);
    GoogleCloudBuildAccountRepository repository =
        GoogleCloudBuildAccountRepository.builder()
            .registerAccount("present", presentAccount)
            .build();

    assertThat(repository.getGoogleCloudBuild("present")).isEqualTo(presentAccount);
  }

  @Test
  public void getAccountsEmpty() {
    GoogleCloudBuildAccountRepository repository =
        GoogleCloudBuildAccountRepository.builder().build();

    assertThat(repository.getAccounts()).isEmpty();
  }

  @Test
  public void getAccountsNotEmpty() {
    GoogleCloudBuildAccount presentAccount = mock(GoogleCloudBuildAccount.class);
    GoogleCloudBuildAccountRepository repository =
        GoogleCloudBuildAccountRepository.builder()
            .registerAccount("present", presentAccount)
            .build();

    assertThat(repository.getAccounts()).containsExactly("present");
  }
}
