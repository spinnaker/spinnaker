/*
 * Copyright 2022 Apple Inc.
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

package com.netflix.spinnaker.credentials.jackson;

import com.fasterxml.jackson.annotation.JacksonAnnotation;
import java.lang.annotation.*;

/**
 * Indicates that a field is considered sensitive and should not be serialized unless it's a secret
 * reference. Secret references are URIs of the form {@code encrypted:...} or {@code secret://...}
 * as documented in kork-secrets. This is useful for preventing accidental secret leakage when
 * displaying credentials definitions or storing them.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@Documented
@JacksonAnnotation
public @interface Sensitive {}
