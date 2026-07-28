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

package com.netflix.spinnaker.security.s2s;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a controller method (or every method of a controller type) to a codified set of
 * internal {@link SpinnakerService} callers.
 *
 * <p>This <em>is</em> the authorization policy, expressed in code and reviewed in the same change
 * that adds the endpoint — never widened from configuration. It is enforced by {@link
 * ServiceCallerEnforcementAspect} using the {@link ServiceCaller} authenticated for the request:
 *
 * <pre>{@code
 * @PostMapping("/auth/issueExecutionToken")
 * @AllowServiceCallers(SpinnakerService.ORCA)
 * public RunAsTokenResponse issueExecutionToken(...) { ... }
 * }</pre>
 *
 * <p>Enforcement only applies when service-to-service auth is enabled ({@code
 * authz.s2s.enabled=true}); it is otherwise inert, so annotating an endpoint is safe before an
 * install opts in. When enabled, a disallowed caller is always denied with 403.
 *
 * <p><strong>Self-invocation caveat:</strong> enforcement is Spring proxy-based AOP, so it only
 * fires when the annotated method is invoked through the Spring proxy — i.e. via normal MVC request
 * dispatch. An internal call from another method on the same bean ({@code this.method()}) bypasses
 * the proxy and is <em>not</em> checked. Apply this only to endpoints reached through request
 * dispatch, never rely on it to guard internally invoked methods.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AllowServiceCallers {

  /** The services permitted to invoke the annotated endpoint. */
  SpinnakerService[] value();
}
