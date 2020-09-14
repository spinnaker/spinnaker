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

package com.netflix.spinnaker.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.*;
import org.junit.jupiter.api.Test;

public class CompositeCredentialsRepositoryTest {

  @Test
  public void TestMultiComposite() {
    CredentialsRepository<Credentials> repo1 = mock(CredentialsRepository.class);
    Credentials cred1 = mock(Credentials.class);
    when(repo1.getType()).thenReturn("type1");
    when(repo1.getAll()).thenReturn(new HashSet<>(Arrays.asList(cred1)));

    CredentialsRepository<?> repo2 = mock(CredentialsRepository.class);
    when(repo2.getType()).thenReturn("type2");
    when(repo2.getAll()).thenReturn(Collections.emptySet());

    CompositeCredentialsRepository<?> composite =
        new CompositeCredentialsRepository<>(Arrays.asList(repo1, repo2));
    assertThat(composite.getAllCredentials()).hasSize(1);
  }
}
