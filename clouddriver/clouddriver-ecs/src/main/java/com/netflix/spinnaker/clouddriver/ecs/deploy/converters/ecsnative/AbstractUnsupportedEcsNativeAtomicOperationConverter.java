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
import java.util.Map;

/**
 * Fails closed for an operation the ecs-native provider registers but does not support, so the
 * request is rejected instead of falling through to classic versioned-service behavior. The
 * description still converts so validation and logging behave like any other operation.
 *
 * <p>Like {@link AbstractEcsNativeAtomicOperationConverter}, this base is unannotated and only the
 * concrete {@code @EcsNativeOperation} subclasses are routed.
 */
public abstract class AbstractUnsupportedEcsNativeAtomicOperationConverter<
        D extends AbstractAmazonCredentialsDescription>
    extends AbstractEcsNativeAtomicOperationConverter<D> {

  private final String unsupportedMessage;

  protected AbstractUnsupportedEcsNativeAtomicOperationConverter(
      Class<D> descriptionType, String unsupportedMessage) {
    super(
        descriptionType,
        description -> {
          throw new UnsupportedOperationException(unsupportedMessage);
        });
    this.unsupportedMessage = unsupportedMessage;
  }

  /** Rejects before converting the description, so missing credentials can't mask the reason. */
  @Override
  public AtomicOperation convertOperation(Map input) {
    throw new UnsupportedOperationException(unsupportedMessage);
  }
}
