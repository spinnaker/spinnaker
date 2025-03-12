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

import static org.mockito.Mockito.*;

import com.netflix.spinnaker.kork.exceptions.InvalidCredentialsTypeException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MapBackedCredentialsRepositoryTest {

  @Test
  public void testEventHandlerCalled() {
    final String TYPE = "myType";
    final String CRED_NAME = "cred";
    CredentialsLifecycleHandler<Credentials> handler = mock(CredentialsLifecycleHandler.class);
    MapBackedCredentialsRepository<Credentials> repository =
        new MapBackedCredentialsRepository<>(TYPE, handler);

    Credentials c1 = mock(Credentials.class);
    when(c1.getName()).thenReturn(CRED_NAME);
    when(c1.getType()).thenReturn(TYPE);

    repository.save(c1);
    // A second time to update
    repository.save(c1);
    repository.delete(CRED_NAME);

    verify(handler, times(1)).credentialsAdded(c1);
    verify(handler, times(1)).credentialsUpdated(c1);
    verify(handler, times(1)).credentialsDeleted(c1);
  }

  @Test
  public void testTypeCheck() {
    final String TYPE1 = "type1";
    final String TYPE2 = "type2";
    final String CRED_NAME = "cred";
    MapBackedCredentialsRepository<Credentials> repository =
        new MapBackedCredentialsRepository<>(TYPE1, null);

    Credentials c1 = mock(Credentials.class);
    when(c1.getName()).thenReturn(CRED_NAME);
    when(c1.getType()).thenReturn(TYPE2);

    Assertions.assertThrows(InvalidCredentialsTypeException.class, () -> repository.save(c1));
  }
}
