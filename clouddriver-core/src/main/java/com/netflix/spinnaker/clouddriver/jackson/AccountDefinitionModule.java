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
package com.netflix.spinnaker.clouddriver.jackson;

import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.spinnaker.clouddriver.jackson.mixins.CredentialsDefinitionMixin;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import java.util.List;

/**
 * Jackson module to register {@link CredentialsDefinition} type discriminators for the provided
 * account definition types. Type discriminators are determined by the presence of a
 * {@code @JsonTypeName} annotation. Plugins should export their account definition types via {@link
 * com.netflix.spinnaker.clouddriver.security.AccountDefinitionTypeProvider} beans.
 *
 * @see
 *     com.netflix.spinnaker.clouddriver.config.AccountDefinitionConfiguration#accountDefinitionModule(List)
 */
public class AccountDefinitionModule extends SimpleModule {

  private final NamedType[] accountDefinitionTypes;

  public AccountDefinitionModule(NamedType... accountDefinitionTypes) {
    super("Clouddriver Account Definition API");
    this.accountDefinitionTypes = accountDefinitionTypes;
  }

  @Override
  public void setupModule(SetupContext context) {
    super.setupModule(context);
    context.setMixInAnnotations(CredentialsDefinition.class, CredentialsDefinitionMixin.class);
    context.registerSubtypes(accountDefinitionTypes);
  }
}
