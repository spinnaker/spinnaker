package com.netflix.spinnaker.clouddriver.lambda.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.amazonaws.services.lambda.model.GetPolicyResult;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration;
import com.netflix.spinnaker.config.LambdaServiceConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LambdaServiceTest {

  private ObjectMapper objectMapper = new ObjectMapper();
  private AmazonClientProvider clientProvider = mock(AmazonClientProvider.class);
  private LambdaServiceConfig lambdaServiceConfig = mock(LambdaServiceConfig.class);
  private ServiceLimitConfiguration serviceLimitConfiguration =
      mock(ServiceLimitConfiguration.class);
  private String REGION = "us-west-2";
  private NetflixAmazonCredentials netflixAmazonCredentials = mock(NetflixAmazonCredentials.class);

  @BeforeEach
  public void makeSureBaseSettings() {
    when(netflixAmazonCredentials.getLambdaEnabled()).thenReturn(true);
  }

  @Test
  void getAllFunctionsWhenFunctionsResultIsNullExpectEmpty() throws InterruptedException {
    when(lambdaServiceConfig.getRetry()).thenReturn(new LambdaServiceConfig.Retry());
    when(serviceLimitConfiguration.getLimit(any(), any(), any(), any(), any())).thenReturn(1.0);
    AWSLambda lambda = mock(AWSLambda.class); // returns null by default
    when(clientProvider.getAmazonLambda(any(), any(), any())).thenReturn(lambda);

    LambdaService lambdaService =
        new LambdaService(
            clientProvider, netflixAmazonCredentials, REGION, objectMapper, lambdaServiceConfig);

    List<Map<String, Object>> allFunctions = lambdaService.getAllFunctions();

    assertEquals(0, allFunctions.size());
  }

  @Test
  void getAllFunctionsWhenFunctionsResultIsEmptyExpectEmpty() throws InterruptedException {
    when(lambdaServiceConfig.getRetry()).thenReturn(new LambdaServiceConfig.Retry());
    when(serviceLimitConfiguration.getLimit(any(), any(), any(), any(), any())).thenReturn(1.0);

    ListFunctionsResult functionsResult = mock(ListFunctionsResult.class);
    when(functionsResult.getFunctions()).thenReturn(List.of()); // Empty list

    AWSLambda lambda = mock(AWSLambda.class);
    when(lambda.listFunctions()).thenReturn(functionsResult);
    when(clientProvider.getAmazonLambda(any(), any(), any())).thenReturn(lambda);

    LambdaService lambdaService =
        new LambdaService(
            clientProvider, netflixAmazonCredentials, REGION, objectMapper, lambdaServiceConfig);

    List<Map<String, Object>> allFunctions = lambdaService.getAllFunctions();

    assertEquals(0, allFunctions.size());
  }

  @Test
  void getAllFunctionsWhenFunctionNameIsEmptyExpectEmpty() throws InterruptedException {
    when(lambdaServiceConfig.getRetry()).thenReturn(new LambdaServiceConfig.Retry());
    when(serviceLimitConfiguration.getLimit(any(), any(), any(), any(), any())).thenReturn(1.0);

    ListFunctionsResult functionsResult = mock(ListFunctionsResult.class);
    when(functionsResult.getFunctions()).thenReturn(List.of(new FunctionConfiguration()));

    AWSLambda lambda = mock(AWSLambda.class);
    when(lambda.listFunctions(any())).thenReturn(functionsResult);
    when(clientProvider.getAmazonLambda(any(), any(), any())).thenReturn(lambda);

    LambdaService lambdaService =
        new LambdaService(
            clientProvider, netflixAmazonCredentials, REGION, objectMapper, lambdaServiceConfig);

    List<Map<String, Object>> allFunctions = lambdaService.getAllFunctions();

    assertEquals(0, allFunctions.size());
  }

  @Test
  void getAllFunctionsWhenFunctionNameIsNotEmptyExpectNotEmpty() throws InterruptedException {
    when(lambdaServiceConfig.getRetry()).thenReturn(new LambdaServiceConfig.Retry());
    when(serviceLimitConfiguration.getLimit(any(), any(), any(), any(), any())).thenReturn(1.0);

    ListFunctionsResult functionsResult = mock(ListFunctionsResult.class);
    FunctionConfiguration functionConfiguration = new FunctionConfiguration();
    functionConfiguration.setFunctionName("testFunction");
    when(functionsResult.getFunctions()).thenReturn(List.of(functionConfiguration));

    AWSLambda lambda = mock(AWSLambda.class);
    when(lambda.listFunctions(any())).thenReturn(functionsResult);
    GetFunctionResult functionResult = new GetFunctionResult();
    functionResult.setConfiguration(functionConfiguration);
    when(lambda.getFunction(any())).thenReturn(functionResult);
    GetPolicyResult getPolicyResult = new GetPolicyResult();
    getPolicyResult.setPolicy(
        "{\n"
            + "  \"Version\": \"2012-10-17\",\n"
            + "  \"Statement\": [\n"
            + "    {\n"
            + "      \"Sid\": \"FirstStatement\",\n"
            + "      \"Effect\": \"Allow\",\n"
            + "      \"Action\": [\"iam:ChangePassword\"],\n"
            + "      \"Resource\": \"*\"\n"
            + "    },\n"
            + "    {\n"
            + "      \"Sid\": \"SecondStatement\",\n"
            + "      \"Effect\": \"Allow\",\n"
            + "      \"Action\": \"s3:ListAllMyBuckets\",\n"
            + "      \"Resource\": \"*\"\n"
            + "    },\n"
            + "    {\n"
            + "      \"Sid\": \"ThirdStatement\",\n"
            + "      \"Effect\": \"Allow\",\n"
            + "      \"Principal\": {\"AWS\":[ \"elasticloadbalancing.amazonaws.com\"]},\n"
            + "      \"Action\": [\n"
            + "        \"lambda:InvokeFunction\",\n"
            + "        \"s3:List*\",\n"
            + "        \"s3:Get*\"\n"
            + "      ],\n"
            + "      \"Resource\": [\n"
            + "        \"arn:aws:s3:::confidential-data\",\n"
            + "        \"arn:aws:s3:::confidential-data/*\"\n"
            + "      ],\n"
            + "      \"Condition\": {\"ArnLike\":{ \"AWS:SourceArn\": \"arn:aws:elasticloadbalancing:something:something:targetgroup/targetGroupName/abc\"}}\n"
            + "    }\n"
            + "  ]\n"
            + "}");
    when(lambda.getPolicy(any())).thenReturn(getPolicyResult);
    when(clientProvider.getAmazonLambda(any(), any(), any())).thenReturn(lambda);

    LambdaService lambdaService =
        new LambdaService(
            clientProvider, netflixAmazonCredentials, REGION, objectMapper, lambdaServiceConfig);

    List<Map<String, Object>> allFunctions = lambdaService.getAllFunctions();

    assertEquals(1, allFunctions.size());
    Map<String, Object> function = allFunctions.get(0);
    assertEquals("testFunction", function.get("functionName"));
  }
}
