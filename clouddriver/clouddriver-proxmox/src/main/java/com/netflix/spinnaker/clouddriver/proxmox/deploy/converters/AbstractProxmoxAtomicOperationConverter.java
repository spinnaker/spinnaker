/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.clouddriver.proxmox.deploy.converters;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.proxmox.deploy.description.ProxmoxBaseDescription;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import java.util.function.Function;

/**
 * Base converter for all Proxmox atomic operations.
 *
 * <p>Subclasses supply the description class and an operation factory; this class handles the
 * identical boilerplate of Jackson deserialization and credentials injection.
 */
@SuppressWarnings("rawtypes")
abstract class AbstractProxmoxAtomicOperationConverter<D extends ProxmoxBaseDescription>
    extends AbstractAtomicOperationsCredentialsSupport {

  private final Class<D> descriptionClass;
  private final Function<D, AtomicOperation> operationFactory;

  protected AbstractProxmoxAtomicOperationConverter(
      Class<D> descriptionClass, Function<D, AtomicOperation> operationFactory) {
    this.descriptionClass = descriptionClass;
    this.operationFactory = operationFactory;
  }

  @Override
  public AtomicOperation convertOperation(Map<String, Object> input) {
    return operationFactory.apply(convertDescription(input));
  }

  @Override
  public D convertDescription(Map<String, Object> input) {
    D description = getObjectMapper().convertValue(input, descriptionClass);
    // Pipeline deploy stages strip "credentials" from stored clusters, leaving only "account".
    Object credentials = input.getOrDefault("credentials", input.get("account"));
    description.setCredentials(getCredentialsObject(credentials.toString()));
    return description;
  }
}
