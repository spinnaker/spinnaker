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
package com.netflix.spinnaker.clouddriver.haproxy.deploy.converters;

import com.netflix.spinnaker.clouddriver.haproxy.deploy.description.HaProxyBaseDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import java.util.function.Function;

/** Binds an operation input map to its description and wraps it in the atomic operation. */
@SuppressWarnings("rawtypes")
abstract class AbstractHaProxyAtomicOperationConverter<D extends HaProxyBaseDescription>
    extends AbstractAtomicOperationsCredentialsSupport {

  private final Class<D> descriptionClass;
  private final Function<D, AtomicOperation> operationFactory;

  protected AbstractHaProxyAtomicOperationConverter(
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
    description.setCredentials(getCredentialsObject(input.get("credentials").toString()));
    return description;
  }
}
