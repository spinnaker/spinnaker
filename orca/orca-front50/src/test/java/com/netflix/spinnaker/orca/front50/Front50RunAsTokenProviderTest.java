/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.orca.front50;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.orca.front50.model.ExecutionTokenRequest;
import com.netflix.spinnaker.orca.front50.model.RunAsTokenResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
class Front50RunAsTokenProviderTest {

  private static final String SUBJECT = "user@example.com";
  private static final List<String> ROLES = List.of("dev", "ops");

  @Mock private Front50Service front50Service;

  private static RunAsTokenResponse tokenResponse(String token) {
    RunAsTokenResponse response = new RunAsTokenResponse();
    response.setToken(token);
    response.setSubject(SUBJECT);
    response.setRoles(ROLES);
    return response;
  }

  @Test
  void issuesTokenAndRelaysAdmittedSubjectAndRoles() {
    when(front50Service.issueExecutionToken(
            argThat(
                request ->
                    SUBJECT.equals(request.getSubject())
                        && request.getRoles().equals(ROLES)
                        && request.isAdmin()
                        && request.isAccountManager())))
        .thenReturn(Calls.response(tokenResponse("minted-token")));
    Front50RunAsTokenProvider provider = new Front50RunAsTokenProvider(front50Service, true);

    Optional<String> token = provider.issueExecutionToken(SUBJECT, ROLES, true, true);

    assertThat(token).contains("minted-token");
  }

  @Test
  void returnsEmptyWhenDisabled() {
    Front50RunAsTokenProvider provider = new Front50RunAsTokenProvider(front50Service, false);

    assertThat(provider.issueExecutionToken(SUBJECT, ROLES, false, false)).isEmpty();
    verifyNoInteractions(front50Service);
  }

  @Test
  void returnsEmptyWhenFront50ServiceIsNull() {
    Front50RunAsTokenProvider provider = new Front50RunAsTokenProvider(null, true);

    assertThat(provider.issueExecutionToken(SUBJECT, ROLES, false, false)).isEmpty();
  }

  @Test
  void returnsEmptyForBlankSubjectWithoutCallingFront50() {
    Front50RunAsTokenProvider provider = new Front50RunAsTokenProvider(front50Service, true);

    assertThat(provider.issueExecutionToken("   ", ROLES, false, false)).isEmpty();
    verify(front50Service, never()).issueExecutionToken(any());
  }

  @Test
  void returnsEmptyWhenResponseHasBlankToken() {
    when(front50Service.issueExecutionToken(any())).thenReturn(Calls.response(tokenResponse(" ")));
    Front50RunAsTokenProvider provider = new Front50RunAsTokenProvider(front50Service, true);

    assertThat(provider.issueExecutionToken(SUBJECT, ROLES, false, false)).isEmpty();
  }

  @Test
  void swallowsFront50FailureAndReturnsEmpty() {
    when(front50Service.issueExecutionToken(any()))
        .thenReturn(Calls.failure(new IOException("front50 down")));
    Front50RunAsTokenProvider provider = new Front50RunAsTokenProvider(front50Service, true);

    assertThat(provider.issueExecutionToken(SUBJECT, ROLES, false, false)).isEmpty();
  }

  @Test
  void toleratesNullRolesByRelayingEmptyRoles() {
    when(front50Service.issueExecutionToken(
            argThat((ExecutionTokenRequest request) -> request.getRoles().isEmpty())))
        .thenReturn(Calls.response(tokenResponse("minted-token")));
    Front50RunAsTokenProvider provider = new Front50RunAsTokenProvider(front50Service, true);

    assertThat(provider.issueExecutionToken(SUBJECT, null, false, false)).contains("minted-token");
  }
}
