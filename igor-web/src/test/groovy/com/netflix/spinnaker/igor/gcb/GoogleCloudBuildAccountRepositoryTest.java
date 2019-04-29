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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GoogleCloudBuildAccountRepositoryTest {

  @Test(expected = NotFoundException.class)
  public void emptyRepository() {
    GoogleCloudBuildAccountRepository repository = new GoogleCloudBuildAccountRepository();
    repository.getGoogleCloudBuild("missing");
  }

  @Test(expected = NotFoundException.class)
  public void missingAccount() {
    GoogleCloudBuildAccountRepository repository = new GoogleCloudBuildAccountRepository();

    GoogleCloudBuildAccount presentAccount = mock(GoogleCloudBuildAccount.class);
    repository.registerAccount("present", presentAccount);

    repository.getGoogleCloudBuild("missing");
  }

  @Test
  public void presentAccount() {
    GoogleCloudBuildAccountRepository repository = new GoogleCloudBuildAccountRepository();

    GoogleCloudBuildAccount presentAccount = mock(GoogleCloudBuildAccount.class);
    repository.registerAccount("present", presentAccount);

    GoogleCloudBuildAccount retrievedAccount = repository.getGoogleCloudBuild("present");
    assertSame(presentAccount, retrievedAccount);
  }

  @Test
  public void getAccounts() {
    GoogleCloudBuildAccountRepository repository = new GoogleCloudBuildAccountRepository();

    GoogleCloudBuildAccount presentAccount = mock(GoogleCloudBuildAccount.class);

    List<String> accountsBefore = repository.getAccounts();
    assertTrue(accountsBefore.isEmpty());

    repository.registerAccount("present", presentAccount);

    List<String> accountsAfter = repository.getAccounts();
    assertEquals(accountsAfter, Collections.singletonList("present"));
  }
}
