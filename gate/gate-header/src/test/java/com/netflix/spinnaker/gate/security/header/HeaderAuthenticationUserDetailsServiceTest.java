/*
 * Copyright 2026 Salesforce, Inc.
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

package com.netflix.spinnaker.gate.security.header;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.gate.model.front50.ServiceAccountPojo;
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class HeaderAuthenticationUserDetailsServiceTest {

  private PermissionService permissionService = mock(PermissionService.class);
  private AllowedAccountsSupport allowedAccountsSupport = mock(AllowedAccountsSupport.class);
  private Front50Service front50Service = mock(Front50Service.class);
  private HeaderAuthProperties headerAuthProperties = new HeaderAuthProperties();
  private HeaderAuthenticationUserDetailsService userDetailsService =
      new HeaderAuthenticationUserDetailsService(
          permissionService, allowedAccountsSupport, front50Service, headerAuthProperties);

  @BeforeEach
  void setUp() {
    when(allowedAccountsSupport.filterAllowedAccounts(anyString(), any()))
        .thenReturn(Collections.emptySet());
  }

  @Test
  void testServiceAccountDoesNotCallFiatLogin() {
    String serviceAccountEmail = "pipegensvc@salesforce.com";
    ServiceAccountPojo serviceAccount = new ServiceAccountPojo(serviceAccountEmail);
    when(front50Service.getServiceAccountsPojo()).thenReturn(List.of(serviceAccount));

    PreAuthenticatedAuthenticationToken token =
        new PreAuthenticatedAuthenticationToken(serviceAccountEmail, null);

    userDetailsService.createUserDetails(token, Collections.emptyList());

    // Verify permissionService.login() is NOT called for service accounts
    verify(permissionService, never()).login(anyString());
  }

  @Test
  void testRegularUserCallsFiatLogin() {
    String regularUserEmail = "regularuser@salesforce.com";
    when(front50Service.getServiceAccountsPojo()).thenReturn(Collections.emptyList());

    PreAuthenticatedAuthenticationToken token =
        new PreAuthenticatedAuthenticationToken(regularUserEmail, null);

    userDetailsService.createUserDetails(token, Collections.emptyList());

    // Verify permissionService.login() IS called for regular users
    verify(permissionService).login(regularUserEmail);
  }

  @Test
  void testCachedServiceAccountLookup() {
    // With caching, two calls to gate should result in one call to
    // front50Service.getServiceAccountsPojo()

    String serviceAccountEmail = "pipegensvc@salesforce.com";
    ServiceAccountPojo serviceAccount = new ServiceAccountPojo(serviceAccountEmail);
    when(front50Service.getServiceAccountsPojo()).thenReturn(List.of(serviceAccount));

    PreAuthenticatedAuthenticationToken token1 =
        new PreAuthenticatedAuthenticationToken(serviceAccountEmail, null);
    PreAuthenticatedAuthenticationToken token2 =
        new PreAuthenticatedAuthenticationToken(serviceAccountEmail, null);

    // First call - cache miss
    userDetailsService.createUserDetails(token1, Collections.emptyList());
    verify(front50Service, times(1)).getServiceAccountsPojo();

    // Second call - cache hit
    userDetailsService.createUserDetails(token2, Collections.emptyList());

    // Verify front50Service.getServiceAccountsPojo() was called only once total
    verifyNoMoreInteractions(front50Service);
  }

  @Test
  void testIsServiceAccountReturnsFalseForNullEmail() {
    boolean result = userDetailsService.isServiceAccount(null);

    assertThat(result).isFalse();
    verify(front50Service, never()).getServiceAccountsPojo();
  }

  @Test
  void testIsServiceAccountCaseInsensitive() {
    String serviceAccountEmail = "PipeGenSvc@Salesforce.com";
    ServiceAccountPojo serviceAccount = new ServiceAccountPojo("pipegensvc@salesforce.com");
    when(front50Service.getServiceAccountsPojo()).thenReturn(List.of(serviceAccount));

    boolean result = userDetailsService.isServiceAccount(serviceAccountEmail);

    assertThat(result).isTrue();
    verify(front50Service).getServiceAccountsPojo();
  }

  @Test
  void testIsServiceAccountReturnsFalseWhenNotInList() {
    String regularUserEmail = "regularuser@salesforce.com";
    ServiceAccountPojo serviceAccount = new ServiceAccountPojo("pipegensvc@salesforce.com");
    when(front50Service.getServiceAccountsPojo()).thenReturn(List.of(serviceAccount));

    boolean result = userDetailsService.isServiceAccount(regularUserEmail);

    assertThat(result).isFalse();
    verify(front50Service).getServiceAccountsPojo();
  }

  @Test
  void testIsServiceAccountHandlesEmptyServiceAccountList() {
    when(front50Service.getServiceAccountsPojo()).thenReturn(Collections.emptyList());

    boolean result = userDetailsService.isServiceAccount("test@salesforce.com");

    assertThat(result).isFalse();
    verify(front50Service).getServiceAccountsPojo();
  }

  @Test
  void testIsServiceAccountWithMultipleServiceAccounts() {
    String serviceAccountEmail = "pipegensvc@salesforce.com";
    ServiceAccountPojo serviceAccount1 = new ServiceAccountPojo("otherservice@salesforce.com");
    ServiceAccountPojo serviceAccount2 = new ServiceAccountPojo(serviceAccountEmail);
    ServiceAccountPojo serviceAccount3 = new ServiceAccountPojo("anotherservice@salesforce.com");
    when(front50Service.getServiceAccountsPojo())
        .thenReturn(List.of(serviceAccount1, serviceAccount2, serviceAccount3));

    boolean result = userDetailsService.isServiceAccount(serviceAccountEmail);

    assertThat(result).isTrue();
    verify(front50Service).getServiceAccountsPojo();
  }

  @Test
  void testCreateUserDetailsReturnsUserWithEmail() {
    String email = "user@salesforce.com";
    when(front50Service.getServiceAccountsPojo()).thenReturn(Collections.emptyList());

    PreAuthenticatedAuthenticationToken token =
        new PreAuthenticatedAuthenticationToken(email, null);

    org.springframework.security.core.userdetails.UserDetails result =
        userDetailsService.createUserDetails(token, Collections.emptyList());

    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(com.netflix.spinnaker.security.User.class);
    com.netflix.spinnaker.security.User user = (com.netflix.spinnaker.security.User) result;
    assertThat(user.getEmail()).isEqualTo(email);
  }

  @Test
  void testCreateUserDetailsCallsFilterAllowedAccounts() {
    String email = "user@salesforce.com";
    when(front50Service.getServiceAccountsPojo()).thenReturn(Collections.emptyList());

    PreAuthenticatedAuthenticationToken token =
        new PreAuthenticatedAuthenticationToken(email, null);

    userDetailsService.createUserDetails(token, Collections.emptyList());

    verify(allowedAccountsSupport).filterAllowedAccounts(email, Collections.emptySet());
  }

  @Test
  void testCacheExpirationAfterTimeout() {
    String serviceAccountEmail = "pipegensvc@salesforce.com";
    ServiceAccountPojo serviceAccount = new ServiceAccountPojo(serviceAccountEmail);

    // Use a very short cache timeout (0 minutes) for testing
    headerAuthProperties.setServiceAccountCacheTimeoutMinutes(0);
    HeaderAuthenticationUserDetailsService userDetailsServiceWithShortTimeout =
        new HeaderAuthenticationUserDetailsService(
            permissionService, allowedAccountsSupport, front50Service, headerAuthProperties);

    when(front50Service.getServiceAccountsPojo()).thenReturn(List.of(serviceAccount));

    PreAuthenticatedAuthenticationToken token1 =
        new PreAuthenticatedAuthenticationToken(serviceAccountEmail, null);

    // First call - cache miss
    userDetailsServiceWithShortTimeout.createUserDetails(token1, Collections.emptyList());
    verify(front50Service, times(1)).getServiceAccountsPojo();

    PreAuthenticatedAuthenticationToken token2 =
        new PreAuthenticatedAuthenticationToken(serviceAccountEmail, null);

    // Second call - cache expired (using 0 timeout, so immediate expiration), should call Front50
    // again
    userDetailsServiceWithShortTimeout.createUserDetails(token2, Collections.emptyList());
    verify(front50Service, times(2)).getServiceAccountsPojo();
  }
}
