/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.fiat.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * For a component that uses this annotation, the Fiat Spring Security configuration will be
 * applied. This does not mean that Fiat is required to be enabled, just that the whole service has
 * Spring Security layers and filters for requests/responses.
 *
 * <p>With this annotation and Fiat disabled, the biggest difference is the ability to access the
 * Spring Management Server endpoints (/env, /beans, /autoconfig, etc). Most of these endpoints are
 * considered "sensitive", and therefore are disabled from an unauthenticated user requesting them
 * over HTTP. In order to still access them, you must use HTTP Basic authentication. See
 * http://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-endpoints.html for
 * more details.
 *
 * <p>With this annotation and Fiat enabled, @Controller invocations annotated with Fiat
 * authorization checks will be performed and enforced. The above Management Server endpoint
 * information still applies.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(FiatAuthenticationConfig.class)
public @interface EnableFiatAutoConfig {}
