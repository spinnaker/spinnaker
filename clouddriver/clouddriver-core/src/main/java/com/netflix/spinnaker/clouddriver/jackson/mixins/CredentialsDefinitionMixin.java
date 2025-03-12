/*
 * Copyright 2021 Apple Inc.
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

package com.netflix.spinnaker.clouddriver.jackson.mixins;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;

/**
 * Jackson mixin to add a polymorphic type name value. When a {@link
 * com.netflix.spinnaker.credentials.definition.CredentialsDefinition} implementation class is
 * annotated with {@link com.fasterxml.jackson.annotation.JsonTypeName}, then the value of that
 * annotation is used as the {@code type} property value when marshalling and unmarshalling
 * CredentialsDefinition classes. It is recommended that the corresponding cloud provider name for
 * the credentials be used here.
 *
 * @see AccountCredentials#getCloudProvider()
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface CredentialsDefinitionMixin {}
