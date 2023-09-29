/*
 * Copyright 2020 Playtika, Inc.
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

package com.netflix.kayenta.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MapBackedStorageServiceRepositoryTest {

  private static final StorageService STORAGE_1 = storageService("account-1");
  private List<StorageService> services =
      Arrays.asList(STORAGE_1, storageService("account-2"), storageService("account-3"));

  StorageServiceRepository repository = new MapBackedStorageServiceRepository(services);

  @Test
  public void getOne_returnsExistingStorageService() {
    assertThat(repository.getOne("account-1")).hasValue(STORAGE_1);
  }

  @Test
  public void getOne_returnsEmptyIfAccountNameNotSupported() {
    assertThat(repository.getOne("unknown")).isEmpty();
  }

  @Test
  public void getRequiredOne_returnsExistingStorageService() {
    assertThat(repository.getRequiredOne("account-1")).isEqualTo(STORAGE_1);
  }

  @Test
  public void getRequiredOne_throwsExceptionIfAccountNameNotSupported() {
    assertThatThrownBy(() -> repository.getRequiredOne("unknown"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static StorageService storageService(String account) {
    StorageService mock = mock(StorageService.class);
    when(mock.servicesAccount(account)).thenReturn(true);
    return mock;
  }
}
