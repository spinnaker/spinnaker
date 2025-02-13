/*
 * Copyright 2016 Google, Inc.
 * Copyright 2023 Apple, Inc.
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

package com.netflix.spinnaker.gate.services;

import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.gate.retrofit.UpstreamBadRequest;
import com.netflix.spinnaker.gate.security.SpinnakerUser;
import com.netflix.spinnaker.gate.services.internal.ExtendedFiatService;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.User;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Log4j2
@Component
@RequiredArgsConstructor
public class PermissionService {
  private final FiatService fiatService;
  private final ExtendedFiatService extendedFiatService;
  private final ServiceAccountFilterConfigProps serviceAccountFilterConfigProps;
  private final FiatPermissionEvaluator permissionEvaluator;
  private final FiatStatus fiatStatus;

  @Setter(
      onParam_ = {@Qualifier("fiatLoginService")},
      onMethod_ = {@Autowired(required = false)})
  private FiatService fiatLoginService;

  public boolean isEnabled() {
    return fiatStatus.isEnabled();
  }

  private FiatService getFiatServiceForLogin() {
    return fiatLoginService != null ? fiatLoginService : fiatService;
  }

  public void login(final String userId) {
    if (fiatStatus.isEnabled()) {
      try {
        AuthenticatedRequest.allowAnonymous(
            () -> {
              Retrofit2SyncCall.execute(getFiatServiceForLogin().loginUser(userId));
              permissionEvaluator.invalidatePermission(userId);
              return null;
            });
      } catch (SpinnakerServerException e) {
        throw UpstreamBadRequest.classifyError(e);
      }
    }
  }

  public void loginWithRoles(final String userId, final Collection<String> roles) {
    if (fiatStatus.isEnabled()) {
      try {
        AuthenticatedRequest.allowAnonymous(
            () -> {
              Retrofit2SyncCall.execute(getFiatServiceForLogin().loginWithRoles(userId, roles));
              permissionEvaluator.invalidatePermission(userId);
              return null;
            });
      } catch (SpinnakerServerException e) {
        throw UpstreamBadRequest.classifyError(e);
      }
    }
  }

  public void logout(String userId) {
    if (fiatStatus.isEnabled()) {
      try {
        Retrofit2SyncCall.execute(getFiatServiceForLogin().logoutUser(userId));
        permissionEvaluator.invalidatePermission(userId);
      } catch (SpinnakerServerException e) {
        throw UpstreamBadRequest.classifyError(e);
      }
    }
  }

  public void sync() {
    if (fiatStatus.isEnabled()) {
      try {
        Retrofit2SyncCall.execute(getFiatServiceForLogin().sync(List.of()));
      } catch (SpinnakerServerException e) {
        throw UpstreamBadRequest.classifyError(e);
      }
    }
  }

  public Set<Role.View> getRoles(String userId) {
    if (!fiatStatus.isEnabled()) {
      return Set.of();
    }
    try {
      var permission = permissionEvaluator.getPermission(userId);
      var roles = permission != null ? permission.getRoles() : null;
      return roles != null ? roles : Set.of();
    } catch (SpinnakerServerException e) {
      throw UpstreamBadRequest.classifyError(e);
    }
  }

  List<UserPermission.View> lookupServiceAccounts(String userId) {
    try {
      return Retrofit2SyncCall.execute(extendedFiatService.getUserServiceAccounts(userId));
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() == 404) {
        return List.of();
      }
      boolean shouldRetry = HttpStatus.valueOf(e.getResponseCode()).is5xxServerError();
      throw new SystemException("getUserServiceAccounts failed", e).setRetryable(shouldRetry);
    } catch (SpinnakerServerException e) {
      throw new SystemException("getUserServiceAccounts failed", e).setRetryable(e.getRetryable());
    }
  }

  public List<String> getServiceAccountsForApplication(
      @SpinnakerUser final User user, @Nonnull final String application) {
    var matchAuthorizations = serviceAccountFilterConfigProps.getMatchAuthorizations();
    boolean requiresFiltering =
        fiatStatus.isEnabled()
            && serviceAccountFilterConfigProps.isEnabled()
            && user != null
            && StringUtils.hasLength(application)
            && !CollectionUtils.isEmpty(matchAuthorizations);
    if (!requiresFiltering) {
      return getServiceAccounts(user);
    }

    List<String> filteredServiceAccounts;
    RetrySupport retry = new RetrySupport();
    try {
      var serviceAccounts =
          retry.retry(
              () -> lookupServiceAccounts(user.getUsername()), 3, Duration.ofMillis(50), false);
      filteredServiceAccounts =
          serviceAccounts.stream()
              .filter(
                  permission ->
                      permission.getApplications().stream()
                          .anyMatch(
                              app ->
                                  application.equalsIgnoreCase(app.getName())
                                      && !Collections.disjoint(
                                          matchAuthorizations, app.getAuthorizations())))
              .map(UserPermission.View::getName)
              .collect(Collectors.toList());
    } catch (SpinnakerException se) {
      log.error(
          "failed to lookup user {} service accounts for application {}, falling back to all user service accounts",
          user,
          application,
          se);
      return getServiceAccounts(user);
    }

    // if there are no service accounts for the requested application, fall back to the full list of
    // service accounts for the user to avoid a chicken and egg problem of trying to enable security
    // for the first time on an application
    return !filteredServiceAccounts.isEmpty() ? filteredServiceAccounts : getServiceAccounts(user);
  }

  public List<String> getServiceAccounts(@SpinnakerUser User user) {
    if (user == null) {
      log.debug("getServiceAccounts: Spinnaker user is null.");
      return List.of();
    }

    if (!fiatStatus.isEnabled()) {
      log.debug("getServiceAccounts: Fiat disabled.");
      return List.of();
    }

    try {
      var permission = permissionEvaluator.getPermission(user.getUsername());
      if (permission == null) {
        return List.of();
      }
      return permission.getServiceAccounts().stream()
          .map(ServiceAccount.View::getName)
          .collect(Collectors.toList());
    } catch (SpinnakerServerException e) {
      throw UpstreamBadRequest.classifyError(e);
    }
  }

  public boolean isAdmin(String userId) {
    var permission = permissionEvaluator.getPermission(userId);
    return permission != null && permission.isAdmin();
  }
}
