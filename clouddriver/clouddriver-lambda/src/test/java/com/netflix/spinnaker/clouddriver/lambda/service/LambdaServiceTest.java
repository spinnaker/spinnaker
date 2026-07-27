package com.netflix.spinnaker.clouddriver.lambda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration;
import com.netflix.spinnaker.config.LambdaServiceConfig;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.FunctionCodeLocation;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.GetPolicyRequest;
import software.amazon.awssdk.services.lambda.model.GetPolicyResponse;
import software.amazon.awssdk.services.lambda.model.ListAliasesRequest;
import software.amazon.awssdk.services.lambda.model.ListAliasesResponse;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsRequest;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionRequest;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;
import software.amazon.awssdk.services.lambda.paginators.ListAliasesIterable;
import software.amazon.awssdk.services.lambda.paginators.ListEventSourceMappingsIterable;
import software.amazon.awssdk.services.lambda.paginators.ListFunctionsIterable;
import software.amazon.awssdk.services.lambda.paginators.ListVersionsByFunctionIterable;

class LambdaServiceTest {

  private ObjectMapper objectMapper = new ObjectMapper().registerModule(new AwsSdkV2Module());
  private AmazonClientProvider clientProvider = mock(AmazonClientProvider.class);
  private LambdaServiceConfig lambdaServiceConfig = mock(LambdaServiceConfig.class);
  private ServiceLimitConfiguration serviceLimitConfiguration =
      mock(ServiceLimitConfiguration.class);
  private String REGION = "us-west-2";
  private NetflixAmazonCredentials netflixAmazonCredentials = mock(NetflixAmazonCredentials.class);

  @BeforeEach
  public void makeSureBaseSettings() {
    when(netflixAmazonCredentials.isLambdaEnabled()).thenReturn(true);
  }

  @Test
  void getAllFunctionsWhenFunctionsResultIsNullExpectEmpty() throws InterruptedException {
    when(lambdaServiceConfig.getRetry()).thenReturn(new LambdaServiceConfig.Retry());
    when(serviceLimitConfiguration.getLimit(any(), any(), any(), any(), any())).thenReturn(1.0);
    LambdaClient lambda = mock(LambdaClient.class); // returns null by default
    when(clientProvider.getLambdaV2(any(), any(), any())).thenReturn(lambda);

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

    ListFunctionsResponse functionsResult =
        ListFunctionsResponse.builder().functions(List.of()).build();

    LambdaClient lambda = mock(LambdaClient.class);
    ListFunctionsIterable paginator = forEachOf(ListFunctionsIterable.class, functionsResult);
    when(lambda.listFunctionsPaginator()).thenReturn(paginator);
    when(clientProvider.getLambdaV2(any(), any(), any())).thenReturn(lambda);

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

    ListFunctionsResponse functionsResult =
        ListFunctionsResponse.builder()
            .functions(List.of(FunctionConfiguration.builder().build()))
            .build();

    LambdaClient lambda = mock(LambdaClient.class);
    ListFunctionsIterable paginator = forEachOf(ListFunctionsIterable.class, functionsResult);
    when(lambda.listFunctionsPaginator()).thenReturn(paginator);
    when(clientProvider.getLambdaV2(any(), any(), any())).thenReturn(lambda);

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

    FunctionConfiguration functionConfiguration =
        FunctionConfiguration.builder()
            .functionName("testFunction")
            .runtime("java17")
            .memorySize(512)
            .timeout(30)
            .build();
    ListFunctionsResponse functionsResult =
        ListFunctionsResponse.builder().functions(List.of(functionConfiguration)).build();

    LambdaClient lambda = mock(LambdaClient.class);
    ListFunctionsIterable listFunctionsPaginator =
        forEachOf(ListFunctionsIterable.class, functionsResult);
    when(lambda.listFunctionsPaginator()).thenReturn(listFunctionsPaginator);

    GetFunctionResponse functionResult =
        GetFunctionResponse.builder()
            .configuration(functionConfiguration)
            .code(
                FunctionCodeLocation.builder()
                    .repositoryType("S3")
                    .location("https://example/code")
                    .build())
            .tags(Map.of("app", "myapp", "stack", "prod"))
            .build();
    when(lambda.getFunction(any(GetFunctionRequest.class))).thenReturn(functionResult);

    GetPolicyResponse getPolicyResult =
        GetPolicyResponse.builder()
            .policy(
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
                    + "}")
            .build();
    when(lambda.getPolicy(any(GetPolicyRequest.class))).thenReturn(getPolicyResult);

    ListVersionsByFunctionIterable versionsPaginator =
        forEachOf(
            ListVersionsByFunctionIterable.class,
            ListVersionsByFunctionResponse.builder()
                .versions(FunctionConfiguration.builder().revisionId("rev-1").version("1").build())
                .build());
    when(lambda.listVersionsByFunctionPaginator(any(ListVersionsByFunctionRequest.class)))
        .thenReturn(versionsPaginator);
    ListAliasesIterable aliasesPaginator =
        forEachOf(ListAliasesIterable.class, ListAliasesResponse.builder().build());
    when(lambda.listAliasesPaginator(any(ListAliasesRequest.class))).thenReturn(aliasesPaginator);
    ListEventSourceMappingsIterable eventSourceMappingsPaginator =
        forEachOf(
            ListEventSourceMappingsIterable.class,
            ListEventSourceMappingsResponse.builder().build());
    when(lambda.listEventSourceMappingsPaginator(any(ListEventSourceMappingsRequest.class)))
        .thenReturn(eventSourceMappingsPaginator);

    when(clientProvider.getLambdaV2(any(), any(), any())).thenReturn(lambda);

    LambdaService lambdaService =
        new LambdaService(
            clientProvider, netflixAmazonCredentials, REGION, objectMapper, lambdaServiceConfig);

    List<Map<String, Object>> allFunctions = lambdaService.getAllFunctions();

    assertEquals(1, allFunctions.size());
    Map<String, Object> function = allFunctions.get(0);
    // Verifies the AwsSdkV2Module/SdkPojoSerializer converts v2 model objects (which are not
    // standard Jackson beans) into a populated Map rather than emitting empty/incorrect output.
    assertEquals("testFunction", function.get("functionName"));
    assertEquals("java17", function.get("runtime"));
    assertEquals(512, function.get("memorySize"));
    assertEquals(30, function.get("timeout"));
    // Parity with the v1 implementation: revisions, code, and tags are hydrated on the function.
    assertEquals(Map.of("rev-1", "1"), function.get("revisions"));
    assertEquals(Map.of("app", "myapp", "stack", "prod"), function.get("tags"));
    assertThat(function.get("code")).isNotNull();
  }

  @SuppressWarnings("unchecked")
  private static <T extends SdkIterable<?>> T forEachOf(Class<T> type, Object response) {
    T paginator = mock(type);
    doAnswer(
            invocation -> {
              Consumer<Object> consumer = invocation.getArgument(0);
              consumer.accept(response);
              return null;
            })
        .when(paginator)
        .forEach(any(Consumer.class));
    return paginator;
  }
}
