/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.clouddriver.lambda.deploy.converters;

import com.netflix.spinnaker.clouddriver.lambda.deploy.description.DeleteLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.lambda.deploy.ops.DeleteLambdaAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("deleteLambdaFunction")
public class DeleteLambdaFunctionAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  public AtomicOperation convertOperation(Map input) {
    return new DeleteLambdaAtomicOperation(convertDescription(input));
  }

  @Override
  public DeleteLambdaFunctionDescription convertDescription(Map input) {
    DeleteLambdaFunctionDescription converted = getObjectMapper().convertValue(input, DeleteLambdaFunctionDescription.class);
    converted.setCredentials(getCredentialsObject(input.get("credentials").toString()));

    return converted;
  }
}
