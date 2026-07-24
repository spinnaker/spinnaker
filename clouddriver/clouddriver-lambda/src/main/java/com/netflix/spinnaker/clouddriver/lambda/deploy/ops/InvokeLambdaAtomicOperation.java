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

package com.netflix.spinnaker.clouddriver.lambda.deploy.ops;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionOutputDescription;
import com.netflix.spinnaker.clouddriver.lambda.deploy.exception.LambdaOperationException;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.config.LambdaServiceConfig;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LogType;

@Log4j2
public class InvokeLambdaAtomicOperation
    extends AbstractLambdaAtomicOperation<
        InvokeLambdaFunctionDescription, InvokeLambdaFunctionOutputDescription>
    implements AtomicOperation<InvokeLambdaFunctionOutputDescription> {

  @Autowired private ArtifactDownloader artifactDownloader;
  @Autowired private LambdaServiceConfig lambdaServiceConfig;

  public InvokeLambdaAtomicOperation(InvokeLambdaFunctionDescription description) {
    super(description, "INVOKE_LAMBDA_FUNCTION");
  }

  @Override
  public InvokeLambdaFunctionOutputDescription operate(List priorOutputs) {
    updateTaskStatus("Initializing Invoking AWS Lambda Function Operation...");

    if (description.getPayloadArtifact() != null) {
      String payload = getPayloadFromArtifact(description.getPayloadArtifact());
      return invokeFunction(description.getFunctionName(), payload);
    } else if (description.getPayload() != null) {
      return invokeFunction(description.getFunctionName(), description.getPayload());
    }

    return null;
  }

  private InvokeLambdaFunctionOutputDescription invokeFunction(
      String functionName, String payload) {
    LambdaClient client = getInvokeLambdaClient();

    int timeoutMs;
    if (description.getTimeout() != -1) {
      // UI & API are in seconds, SDK is in MS.
      timeoutMs = description.getTimeout() * 1000;
    } else {
      timeoutMs = lambdaServiceConfig.getInvokeTimeoutMs();
    }

    InvokeRequest.Builder requestBuilder =
        InvokeRequest.builder()
            .functionName(functionName)
            .logType(LogType.TAIL)
            .payload(SdkBytes.fromUtf8String(payload))
            .overrideConfiguration(
                AwsRequestOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofMillis(timeoutMs))
                    .build());

    String qualifierRegex = "|[a-zA-Z0-9$_-]+";
    if (description.getQualifier().matches(qualifierRegex)) {
      requestBuilder.qualifier(description.getQualifier());
    }

    log.info("Invoking Lmabda function {} and waiting for it to complete", functionName);
    InvokeResponse result = client.invoke(requestBuilder.build());
    String ans = result.payload().asString(StandardCharsets.UTF_8);
    InvokeLambdaFunctionOutputDescription is = new InvokeLambdaFunctionOutputDescription();
    is.setInvokeResult(result);
    is.setResponseString(ans);
    updateTaskStatus("Finished Invoking of AWS Lambda Function Operation...");
    return is;
  }

  private String getPayloadFromArtifact(Artifact artifact) {
    Path directory = createEmptyDirectory();
    File payloadFile = downloadFileToDirectory(artifact, directory);
    String payloadString;

    try {
      payloadString = FileUtils.readFileToString(payloadFile, "UTF8");
    } catch (IOException e) {
      throw new LambdaOperationException("Unable to read Artifact file to string.");
    } finally {
      try {
        FileUtils.cleanDirectory(directory.toFile());
        FileUtils.forceDelete(directory.toFile());
      } catch (Exception e) {
        throw new LambdaOperationException("Unable to clean up and delete directory.");
      }
    }

    return payloadString;
  }

  private Path createEmptyDirectory() {
    Path path;
    try {
      path = Files.createTempDirectory("awslambdainvoke-");
      FileUtils.cleanDirectory(path.toFile());
    } catch (IOException ex) {
      throw new LambdaOperationException(
          "Unable to create empty directory for AWS Lambda Invocation.");
    }
    return path;
  }

  private File downloadFileToDirectory(Artifact artifact, Path directory) {
    File targetFile;
    try {
      InputStream inStream = artifactDownloader.download(artifact);
      targetFile = new File(directory + "/ARTIFACT.yaml");
      FileUtils.copyInputStreamToFile(inStream, targetFile);
      IOUtils.closeQuietly(inStream);
    } catch (IOException e) {
      throw new LambdaOperationException("Failed to load payload Artifact.");
    }
    return targetFile;
  }
}
