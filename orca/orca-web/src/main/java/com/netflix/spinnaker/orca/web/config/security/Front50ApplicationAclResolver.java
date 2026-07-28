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

package com.netflix.spinnaker.orca.web.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.authz.application.ApplicationPermissionSource;
import com.netflix.spinnaker.security.authz.application.RemoteApplicationAclResolver;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves {@code application} ACLs for Orca from their authoritative owner, Front50.
 *
 * <p>Orca does not own application ACLs, but it does own execution data: {@code TaskController}
 * serves executions from Orca's own store, so its {@code hasPermission(<app>, 'APPLICATION', ...)}
 * checks are the only enforcement point on that path — there is no downstream owner to re-check a
 * list Orca has already fetched. Without a resolver those checks fall through to the
 * unknown-application branch and every execution is readable (and cancellable) by any authenticated
 * caller, which is what Fiat's evaluator used to prevent.
 *
 * <p>Binds Orca's Front50 client to the shared {@link RemoteApplicationAclResolver}, which owns the
 * record-to-ACL translation and the per-request memoization that keeps {@code @PostFilter} over an
 * execution list to one read per distinct application rather than one per row.
 *
 * <p>Records are read with {@code ?effective=true} so Front50 applies the global default
 * application permissions: that config stays in Front50 alone, and Orca cannot deny an execution
 * that Front50 would have allowed by holding a stale copy of it.
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
        // Marked anonymous only to suppress the unauthenticated-call warning: this lookup is Orca's
        // own, made while deciding a caller's access, not a call the caller asked for. Front50
        // leaves the permission record readable to any authenticated service.
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
