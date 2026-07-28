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

package com.netflix.spinnaker.security.authz.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.ResourceType;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Resolves {@code application} ACLs for a service that does not own them, by reading each record on
 * demand from the authoritative owner (Front50) via an {@link ApplicationPermissionSource}. This is
 * the owner-local replacement for what Fiat's materialized permission view previously supplied to
 * every service.
 *
 * <p>The source is expected to return the <em>effective</em> ACL, i.e. with the global default
 * application permissions already applied by Front50. Consuming services therefore do not read
 * {@code authz.application.default-permissions} at all: it is configured in Front50 alone, so the
 * decision here cannot drift from the one Front50 would make for the same caller and application.
 *
 * <h2>Repeated lookups within one request</h2>
 *
 * <p>{@code @PostFilter} evaluates {@code hasPermission} once per element, so a list response would
 * otherwise re-read the same few applications once per row. Resolved records are therefore memoized
 * for the duration of the current request, reducing the cost to one read per <em>distinct</em>
 * application.
 *
 * <p>This is explicitly not a cache: the memo lives in the request attributes and dies with the
 * request, so no state outlives the response and a permission change is visible on the very next
 * request. On threads with no bound request (e.g. background workers, which do not go through
 * method security) resolution simply proceeds unmemoized.
 */
public class RemoteApplicationAclResolver implements ResourceAclResolver {

  private static final Logger log = LoggerFactory.getLogger(RemoteApplicationAclResolver.class);

  private static final String MEMO_ATTRIBUTE =
      RemoteApplicationAclResolver.class.getName() + ".acls";

  private final ApplicationPermissionSource permissionSource;
  private final ObjectMapper objectMapper;

  public RemoteApplicationAclResolver(
      ApplicationPermissionSource permissionSource, ObjectMapper objectMapper) {
    this.permissionSource = permissionSource;
    this.objectMapper = objectMapper;
  }

  @Nullable
  @Override
  public Permissions resolve(ResourceType resourceType, String resourceName) {
    if (resourceName == null || !ResourceType.APPLICATION.equals(resourceType)) {
      return null;
    }
    return memoized(resourceName);
  }

  /**
   * The application's ACL, read at most once per application per request. {@code null} results are
   * memoized too, so an unknown application is not re-read for every row of a list response.
   */
  @Nullable
  private Permissions memoized(String name) {
    Map<String, Optional<Permissions>> memo = requestMemo();
    if (memo == null) {
      return lookupOwnAcl(name);
    }

    String key = name.toLowerCase(Locale.ROOT);
    Optional<Permissions> existing = memo.get(key);
    if (existing != null) {
      return existing.orElse(null);
    }

    Permissions resolved = lookupOwnAcl(name);
    memo.put(key, Optional.ofNullable(resolved));
    log.debug(
        "Read application ACL for '{}' ({} distinct application(s) resolved this request)",
        name,
        memo.size());
    return resolved;
  }

  /**
   * The per-request memo, created on first use, or {@code null} when no request is bound to the
   * current thread.
   */
  @Nullable
  @SuppressWarnings("unchecked")
  private static Map<String, Optional<Permissions>> requestMemo() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return null;
    }
    Map<String, Optional<Permissions>> memo =
        (Map<String, Optional<Permissions>>)
            attributes.getAttribute(MEMO_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    if (memo == null) {
      memo = new HashMap<>();
      attributes.setAttribute(MEMO_ATTRIBUTE, memo, RequestAttributes.SCOPE_REQUEST);
    }
    return memo;
  }

  @Nullable
  private Permissions lookupOwnAcl(String name) {
    Map<?, ?> record;
    try {
      record = permissionSource.fetch(name);
    } catch (RuntimeException e) {
      log.debug("Unable to resolve application ACL for '{}' from its owner", name, e);
      return null;
    }
    if (record == null) {
      // No permission record, or the owner could not be reached: let the evaluator apply
      // allowAccessToUnknownApplications (when no global defaults are configured).
      return null;
    }
    Object permissions = record.get("permissions");
    if (permissions == null) {
      // A record with no permissions block is unrestricted.
      return Permissions.EMPTY;
    }
    return objectMapper.convertValue(permissions, Permissions.class);
  }
}
