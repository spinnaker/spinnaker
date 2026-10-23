/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.converters.ecsnative;

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import java.util.function.Function;

/**
 * Shared body of the ecs-native converters: maps the input onto the description type, resolves its
 * {@code credentials}, and hands the description to the operation factory.
 *
 * <p>Deliberately carries no {@code @EcsNativeOperation}/{@code @EcsOperation} annotation and is
 * not a bean: {@code AnnotationsBasedAtomicOperationsRegistry} matches converters by the provider
 * annotation on each concrete {@code @Component} class, so only the concrete subclasses take part
 * in operation routing.
 */
public abstract class AbstractEcsNativeAtomicOperationConverter<
        D extends AbstractAmazonCredentialsDescription>
    extends AbstractAtomicOperationsCredentialsSupport {

  private final Class<D> descriptionType;
  private final Function<D, ? extends AtomicOperation> operationFactory;

  protected AbstractEcsNativeAtomicOperationConverter(
      Class<D> descriptionType, Function<D, ? extends AtomicOperation> operationFactory) {
    this.descriptionType = descriptionType;
    this.operationFactory = operationFactory;
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    return operationFactory.apply(convertDescription(input));
  }

  @Override
  public D convertDescription(Map input) {
    D converted = getObjectMapper().convertValue(input, descriptionType);
    converted.setCredentials(getCredentialsObject(input.get("credentials").toString()));
    return converted;
  }
}
