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

package com.netflix.spinnaker.orca.clouddriver.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.io.CharStreams;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.config.LambdaConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.*;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaDeploymentInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaGetInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaVerificationStatusOutput;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import okhttp3.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.pf4j.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaCloudDriverUtils {
  private static final Logger logger = LoggerFactory.getLogger(LambdaCloudDriverUtils.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final String CLOUDDRIVER_GET_PATH = "/functions";

  static {
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
  }

  @Autowired LambdaConfigurationProperties config;

  @Autowired CloudDriverConfigurationProperties props;

  @Autowired OortService oort;

  public LambdaCloudDriverResponse postToCloudDriver(String endPointUrl, String jsonString) {
    return postToCloudDriver(endPointUrl, jsonString, config.getCloudDriverPostRequestRetries());
  }

  public LambdaCloudDriverResponse postToCloudDriver(
      String endPointUrl, String jsonString, int retries) {
    final RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonString);
    final Request request =
        new Request.Builder().url(endPointUrl).headers(buildHeaders()).post(body).build();
    return new RetrySupport()
        .retry(
            () -> {
              try {
                OkHttpClient client = new OkHttpClient();
                Call call = client.newCall(request);
                Response response = call.execute();
                String respString = response.body().string();

                if (200 == response.code() || 202 == response.code()) {
                  logger.debug(respString);
                  return objectMapper.readValue(respString, LambdaCloudDriverResponse.class);
                }

                logger.error("Error calling cloud driver");
                logger.error(respString);

                throw new SpinnakerException("Error calling cloud driver: " + respString)
                    .setRetryable(409 == response.code());
              } catch (Exception e) {
                throw new SpinnakerException(e).setRetryable(false);
              }
            },
            retries,
            Duration.ofSeconds(30),
            false);
  }

  public LambdaCloudDriverInvokeOperationResults getLambdaInvokeResults(String endPoint) {
    String respString = getFromCloudDriver(endPoint);
    LambdaCloudDriverInvokeOperationResults respObject = null;
    try {
      JsonNode jsonResults = objectMapper.readTree(respString);
      ArrayNode resultsNode = (ArrayNode) jsonResults.get("resultObjects");
      if ((resultsNode != null) && resultsNode.isArray() && resultsNode.size() > 0) {
        respObject =
            objectMapper.convertValue(
                resultsNode.get(0), LambdaCloudDriverInvokeOperationResults.class);
        JsonNode respStringNode = objectMapper.readTree(respObject.getResponseString());
        if (respStringNode.has("statusCode")) {
          int statusCode = respStringNode.get("statusCode").intValue();
          respObject.setStatusCode(statusCode);
        }
        if (respStringNode.has("body")) {
          String body = respStringNode.get("body").textValue();
          respObject.setBody(body);
        } else if (respStringNode.has("payload")) {
          String body = respStringNode.get("payload").textValue();
          respObject.setBody(body);
        }
        if (respStringNode.has("errorMessage")) {
          String errorMessage = respStringNode.get("errorMessage").textValue();
          respObject.setErrorMessage(errorMessage);
          respObject.setHasErrors(true);
        } else {
          respObject.setHasErrors(false);
        }
      }
      return respObject;
    } catch (Exception e) {
      logger.error("Failed getLambdaInvokeResults task at {}", endPoint, e);
      return respObject;
    }
  }

  public String getPublishedVersion(String endPoint) {
    String respString = getFromCloudDriver(endPoint);
    try {
      JsonNode jsonResults = objectMapper.readTree(respString);
      ArrayNode resultsNode = (ArrayNode) jsonResults.get("resultObjects");
      if (resultsNode.isArray() && resultsNode.size() > 0) {
        JsonNode result = resultsNode.get(0);
        if (result.has("version")) {
          return result.get("version").textValue();
        }
      }
    } catch (Exception e) {
      logger.error("Failed getPublishedVersion task at {}", endPoint, e);
    }
    return "$LATEST";
  }

  public LambdaCloudDriverTaskResults verifyStatus(String endPoint) {
    String respString = getFromCloudDriver(endPoint);
    try {
      JsonNode jsonResults = objectMapper.readTree(respString);
      JsonNode statusNode = jsonResults.get("status");
      ArrayNode resultsNode = (ArrayNode) jsonResults.get("resultObjects");
      LambdaCloudDriverResultObject ro = null;
      LambdaCloudDriverErrorObject err = null;
      if ((resultsNode != null) && resultsNode.isArray()) {
        ro = objectMapper.convertValue(resultsNode.get(0), LambdaCloudDriverResultObject.class);
        err = objectMapper.convertValue(resultsNode.get(0), LambdaCloudDriverErrorObject.class);
      }
      LambdaVerificationStatusOutput st =
          objectMapper.convertValue(statusNode, LambdaVerificationStatusOutput.class);

      return LambdaCloudDriverTaskResults.builder().results(ro).status(st).errors(err).build();
    } catch (Exception e) {
      logger.error("Failed verifying task at {}", endPoint, e);
      throw new RuntimeException(e);
    }
  }

  public String getFromCloudDriver(String endPoint) {
    Request request = new Request.Builder().url(endPoint).headers(buildHeaders()).get().build();
    OkHttpClient client =
        new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(config.getCloudDriverConnectTimeout()))
            .readTimeout(Duration.ofSeconds(config.getCloudDriverReadTimeout()))
            .build();
    Call call = client.newCall(request);
    try {
      Response response = call.execute();
      return response.body().string();
    } catch (Exception e) {
      logger.error("Exception verifying task", e);
      throw new RuntimeException(e);
    }
  }

  public Headers buildHeaders() {
    Headers.Builder headersBuilder = new Headers.Builder();

    AuthenticatedRequest.getAuthenticationHeaders()
        .forEach(
            (key, value) -> {
              value.ifPresent(s -> headersBuilder.add(key, s));
            });

    return headersBuilder.build();
  }

  public LambdaDefinition retrieveLambdaFromCache(LambdaGetInput inp) {
    // {{clouddriver_url}}/functions?functionName=a1-json_simple_lambda_222&region=us-west-2&account=aws-managed-1
    logger.debug("Retrieve Lambda");
    String cloudDriverUrl = props.getCloudDriverBaseUrl();
    String region = inp.getRegion();
    String acc = inp.getAccount();
    String fName = inp.getFunctionName();
    String appPrefix = String.format("%s-", inp.getAppName());
    if (!fName.startsWith(appPrefix)) {
      fName = String.format("%s-%s", inp.getAppName(), inp.getFunctionName());
    }
    String url = cloudDriverUrl + CLOUDDRIVER_GET_PATH;
    HttpUrl.Builder httpBuilder = HttpUrl.parse(url).newBuilder();
    httpBuilder.addQueryParameter("region", region);
    httpBuilder.addQueryParameter("account", acc);
    httpBuilder.addQueryParameter("functionName", fName);
    Request request =
        new Request.Builder().url(httpBuilder.build()).headers(buildHeaders()).build();
    OkHttpClient client = new OkHttpClient();
    Call call = client.newCall(request);
    try {
      Response response = call.execute();
      if (200 != response.code()) {
        logger.error("Could not retrieve lambda");
        return null;
      }
      logger.debug("Found a function");
      String respString = response.body().string();
      return this.asObjectFromList(respString, LambdaDefinition.class);
    } catch (Exception e) {
      logger.error("Error calling clouddriver to find lambda.", e);
      throw new RuntimeException(e);
    }
  }

  public <T> T getInput(StageExecution stage, Class<T> type) {
    try {
      return objectMapper.convertValue(stage.getContext(), type);
    } catch (Throwable e) {
      e.printStackTrace();
      logger.error("Could not convert value");
    }
    return null;
  }

  public <T> T asObjectFromList(String inpString, Class<T> type) {
    try {
      TypeFactory typeFactory = objectMapper.getTypeFactory();
      List<T> someClassList =
          objectMapper.readValue(inpString, typeFactory.constructCollectionType(List.class, type));
      if (someClassList.size() == 0) {
        return null;
      }
      return someClassList.get(0);
    } catch (Throwable e) {
      e.printStackTrace();
      logger.error("Could not convert value");
    }
    return null;
  }

  public <T> T asObject(String inpString, Class<T> type) {
    try {
      return objectMapper.convertValue(inpString, type);
    } catch (Throwable e) {
      e.printStackTrace();
      logger.error("Could not convert value");
    }
    return null;
  }

  public String asString(Object inp) {
    try {
      return objectMapper.writeValueAsString(inp);
    } catch (JsonProcessingException e) {
      logger.error("Could not jsonify", e);
      throw new RuntimeException(e);
    }
  }

  public String getCanonicalVersion(
      LambdaDefinition lf, String inputVersion, String versionNumber, int retentionNumber) {
    List<String> revisions = getSortedRevisions(lf);
    if (revisions.size() != 0) {
      if (inputVersion.startsWith("$PROVIDED")) { // actual version
        return versionNumber;
      }

      if (inputVersion.startsWith("$LATEST")) { // latest version number
        return revisions.get(0);
      }

      if (inputVersion.startsWith("$OLDEST")) { // oldest version number
        return revisions.get(revisions.size() - 1);
      }

      if (inputVersion.startsWith("$PREVIOUS")) { // latest - 1 version number
        if (revisions.size() >= 2) return revisions.get(1);
        else return null;
      }

      if (inputVersion.startsWith("$MOVING")) { // list of versions
        if (revisions.size() > retentionNumber) {
          List<String> toRemoveList = revisions.subList(retentionNumber, revisions.size());
          return String.join(",", toRemoveList);
        }
      }
      // Couldnt find it.
      logger.error(String.format("Found invalid version string %s", inputVersion));
      return null;
    }
    logger.error("No published versions exist for function.");
    return null;
  }

  public List<String> getSortedRevisions(LambdaDefinition lf) {
    List<String> revisions = new ArrayList<>(lf.getRevisions().values());
    List<Integer> revInt =
        revisions.stream()
            .filter(NumberUtils::isCreatable)
            .map(Integer::valueOf)
            .collect(Collectors.toList());
    revInt = revInt.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    return revInt.stream().map(x -> Integer.toString(x)).collect(Collectors.toList());
  }

  public LambdaDefinition retrieveLambdaFromCache(StageExecution stage, boolean shouldRetry) {
    LambdaGetInput lgi = this.getInput(stage, LambdaGetInput.class);
    lgi.setAppName(stage.getExecution().getApplication());
    LambdaDefinition lf = this.retrieveLambdaFromCache(lgi);
    int count = 0;
    while (lf == null && count < 5 && shouldRetry) {
      count++;
      lf = this.retrieveLambdaFromCache((lgi));
      this.await();
    }
    return lf;
  }

  public boolean validateUpsertLambdaInput(
      LambdaDeploymentInput inputLambda, List<String> errorMessages) {
    if (validateBasicLambdaDeploymentInput(inputLambda, errorMessages)) {
      if (ObjectUtils.defaultIfNull(inputLambda.getEnableLambdaAtEdge(), Boolean.FALSE)) {
        return validateLambdaEdgeInput(inputLambda, errorMessages);
      }
      return true;
    }
    return false;
  }

  public boolean validateBasicLambdaDeploymentInput(
      LambdaDeploymentInput inputLambda, List<String> errorMessages) {
    int numErrors = errorMessages.size();

    if (StringUtils.isNullOrEmpty(inputLambda.getAccount())) {
      errorMessages.add("Account is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getRegion())) {
      errorMessages.add("Region is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getFunctionName())) {
      errorMessages.add("Function Name is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getRuntime())) {
      errorMessages.add("Runtime is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getS3bucket())) {
      errorMessages.add("S3 Bucket is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getS3key())) {
      errorMessages.add("S3 Key is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getHandler())) {
      errorMessages.add("Handler is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getRole())) {
      errorMessages.add("Role ARN is required");
    }
    return errorMessages.size() == numErrors;
  }

  public boolean validateLambdaEdgeInput(
      LambdaDeploymentInput inputLambda, List<String> errorMessages) {
    int numErrors = errorMessages.size();

    if (inputLambda.getEnvVariables() == null || inputLambda.getEnvVariables().size() > 0) {
      errorMessages.add("Edge enabled lambdas cannot have env variables");
    }
    if (inputLambda.getTimeout() > 5) {
      errorMessages.add("Edge enabled lambdas cannot have timeout > 5");
    }
    if (inputLambda.getMemorySize() > 128) {
      errorMessages.add("Edge enabled lambdas cannot have memory > 128");
    }
    if (!inputLambda.getRegion().equals("us-east-1")) {
      errorMessages.add("Edge enabled lambdas need to be deployed in us-east-1 region");
    }
    if (StringUtils.isNotNullOrEmpty(inputLambda.getVpcId())) {
      errorMessages.add("Edge enabled lambdas cannot have vpc associations");
    }
    if (inputLambda.getSubnetIds() == null || inputLambda.getSubnetIds().size() > 0) {
      errorMessages.add("Edge enabled lambdas cannot have subnets");
    }
    if (inputLambda.getSecurityGroupIds() == null || inputLambda.getSecurityGroupIds().size() > 0) {
      errorMessages.add("Edge enabled lambdas cannot have security groups");
    }
    return errorMessages.size() == numErrors;
  }

  public void await() {
    this.await(20000);
  }

  public void await(long duration) {
    try {
      logger.debug("Going to sleep during lambda");
      Thread.sleep(duration);
    } catch (Throwable e) {
      logger.error("Error during await of lambda ", e);
    }
  }

  private Artifact resolvePipelineArtifact(LambdaPipelineArtifact artifact) {
    return Artifact.builder()
        .uuid(artifact.getId())
        .artifactAccount(artifact.getArtifactAccount())
        .type(artifact.getType())
        .reference(artifact.getReference())
        .version(artifact.getVersion())
        .name(artifact.getName())
        .build();
  }

  public String getPipelinesArtifactContent(LambdaPipelineArtifact pipelineArtifact) {
    RetrySupport retrySupport = new RetrySupport();

    return retrySupport.retry(
        () -> {
          retrofit.client.Response response =
              oort.fetchArtifact(resolvePipelineArtifact(pipelineArtifact));
          InputStream artifactInputStream;
          try {
            artifactInputStream = response.getBody().in();
          } catch (IOException e) {
            throw new IllegalStateException(e); // forces a retry
          }
          try (InputStreamReader rd = new InputStreamReader(artifactInputStream)) {
            return CharStreams.toString(rd);
          } catch (IOException e) {
            throw new IllegalStateException(e); // forces a retry
          }
        },
        10,
        Duration.ofMillis(200),
        true);
  }
}
