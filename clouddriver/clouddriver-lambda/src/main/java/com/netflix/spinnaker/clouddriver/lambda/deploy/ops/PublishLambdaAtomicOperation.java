/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.lambda.deploy.ops;

import com.netflix.spinnaker.clouddriver.lambda.deploy.description.PublishLambdaFunctionVersionDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.PublishVersionRequest;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;

public class PublishLambdaAtomicOperation
    extends AbstractLambdaAtomicOperation<
        PublishLambdaFunctionVersionDescription, PublishVersionResponse>
    implements AtomicOperation<PublishVersionResponse> {

  public PublishLambdaAtomicOperation(PublishLambdaFunctionVersionDescription description) {
    super(description, "PUBLISH_LAMBDA_FUNCTION_VERSION");
  }

  @Override
  public PublishVersionResponse operate(List priorOutputs) {
    updateTaskStatus("Initializing Atomic Operation AWS Lambda for PublishVersion...");
    return publishFunctionVersion(
        description.getFunctionName(), description.getDescription(), description.getRevisionId());
  }

  private PublishVersionResponse publishFunctionVersion(
      String functionName, String description, String revisionId) {
    LambdaClient client = getLambdaClient();
    PublishVersionRequest.Builder req =
        PublishVersionRequest.builder().functionName(functionName).description(description);

    if (revisionId != null) {
      req.revisionId(revisionId);
    }

    PublishVersionResponse result = client.publishVersion(req.build());
    updateTaskStatus("Finished Atomic Operation AWS Lambda for PublishVersion...");
    return result;
  }
}
