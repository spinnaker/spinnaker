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
 */

package com.netflix.spinnaker.credentials.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.credentials.Credentials;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import java.util.Arrays;
import java.util.Collections;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

public class BasicCredentialsLoaderTest {
  private static final String TEST_TYPE = "test";

  @Test
  public void testCredentialsLoader() {
    CredentialsDefinitionSource<CredentialsDefinition> source =
        mock(CredentialsDefinitionSource.class);
    CredentialsLifecycleHandler<FakeCredentials> handler = mock(CredentialsLifecycleHandler.class);
    CredentialsRepository<FakeCredentials> repository =
        new MapBackedCredentialsRepository<>(TEST_TYPE, handler);

    BasicCredentialsLoader loader =
        new BasicCredentialsLoader(
            source, account -> new FakeCredentials(account.getName()), repository);

    CredentialsDefinition def1 = mock(CredentialsDefinition.class);
    when(def1.getName()).thenReturn("cred1");

    CredentialsDefinition def2 = mock(CredentialsDefinition.class);
    when(def2.getName()).thenReturn("cred2");

    when(source.getCredentialsDefinitions()).thenReturn(Arrays.asList(def1));
    loader.load();

    // Check we loaded the right account
    assertThat(repository.getAll()).hasSize(1);
    assertThat(repository.getOne("cred1")).isNotNull();
    assertThat(repository.getOne("cred2")).isNull();

    when(source.getCredentialsDefinitions()).thenReturn(Arrays.asList(def2));

    loader.load();
    // Check we loaded the right account
    assertThat(repository.getAll()).hasSize(1);
    assertThat(repository.getOne("cred1")).isNull();
    assertThat(repository.getOne("cred2")).isNotNull();

    when(source.getCredentialsDefinitions()).thenReturn(Collections.emptyList());
    loader.load();
    assertThat(repository.getAll()).isEmpty();
  }

  @RequiredArgsConstructor
  private class FakeCredentials implements Credentials {
    @Getter private final String name;

    @Override
    public String getType() {
      return TEST_TYPE;
    }
  }
}
