/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.clouddriver.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.authz.application.ApplicationPermissionSource;
import com.netflix.spinnaker.security.authz.application.RemoteApplicationAclResolver;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves {@code application} ACLs for Clouddriver from their authoritative owner, Front50, so the
 * {@code hasPermission(<app>, 'APPLICATION', ...)} checks across Clouddriver's controllers evaluate
 * against real permissions instead of being uniformly denied (Clouddriver does not own application
 * ACLs and has no local copy). This is the owner-local replacement for what Fiat's materialized
 * permission view previously supplied.
 *
 * <p>Binds Clouddriver's Front50 client to the shared {@link RemoteApplicationAclResolver}, which
 * owns the record-to-ACL translation, the merge of global default application permissions, and the
 * per-request memoization of repeated lookups.
 *
 * <p>Each check reads the record on demand from {@code GET
 * /permissions/applications/{app}?effective=true}; Front50 applies the global default application
 * permissions, so that config lives in Front50 alone and Clouddriver cannot reach a different
 * decision by holding a stale copy of it. Beyond the current request nothing is cached, so
 * permission changes take effect immediately and Clouddriver holds no fleet-sized permission state.
 * A 404 (no permission record) and a transient Front50 error both resolve to {@code null}, letting
 * the evaluator apply {@code allowAccessToUnknownApplications}.
 */
public class Front50ApplicationAclResolver extends RemoteApplicationAclResolver {

  public Front50ApplicationAclResolver(
      @Nullable Front50Service front50Service, ObjectMapper objectMapper) {
    super(new Front50PermissionSource(front50Service), objectMapper);
  }

  /** Reads a single application's permission record from Front50. */
  private static class Front50PermissionSource implements ApplicationPermissionSource {

    private static final Logger log = LoggerFactory.getLogger(Front50PermissionSource.class);

    @Nullable private final Front50Service front50Service;

    Front50PermissionSource(@Nullable Front50Service front50Service) {
      this.front50Service = front50Service;
    }

    @Nullable
    @Override
    public Map<?, ?> fetch(String applicationName) {
      if (front50Service == null) {
        return null;
      }
      try {
        Map<?, ?> record =
            AuthenticatedRequest.allowAnonymous(
                () ->
                    Retrofit2SyncCall.execute(
                        front50Service.getApplicationPermission(applicationName, true)));
        // A 200 with no body is an application with no permissions, i.e. unrestricted; an empty
        // record conveys that, where null would mean "no record at all".
        return record == null ? Map.of() : record;
      } catch (SpinnakerHttpException e) {
        if (e.getResponseCode() == 404) {
          // No permission record: unrestricted unless global defaults apply.
          return null;
        }
        log.debug("Unable to resolve application ACL for '{}' from Front50", applicationName, e);
        return null;
      } catch (Exception e) {
        log.debug("Unable to resolve application ACL for '{}' from Front50", applicationName, e);
        return null;
      }
    }
  }
}
