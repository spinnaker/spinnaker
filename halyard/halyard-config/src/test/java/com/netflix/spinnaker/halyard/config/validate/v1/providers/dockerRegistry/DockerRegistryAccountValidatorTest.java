package com.netflix.spinnaker.halyard.config.validate.v1.providers.dockerRegistry;

import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerToken;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryCatalog;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.FileService;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import com.netflix.spinnaker.kork.configserver.CloudConfigResourceService;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;
import com.netflix.spinnaker.kork.secrets.SecretEngineRegistry;
import com.netflix.spinnaker.kork.secrets.SecretManager;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(
    classes = {
      SecretSessionManager.class,
      SecretManager.class,
      SecretEngineRegistry.class,
      FileService.class,
      ConfigFileService.class,
      CloudConfigResourceService.class,
      DockerRegistryAccountValidatorTest.TestConfig.class
    })
public class DockerRegistryAccountValidatorTest {
  @Autowired ApplicationContext context;

  @Autowired SecretSessionManager secretSessionManager;

  @Autowired FileService fileService;

  @Autowired DockerRegistryAccountValidator validator;

  @RegisterExtension
  static WireMockExtension wmDocker =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  ConfigProblemSetBuilder configProblemSetBuilder = new ConfigProblemSetBuilder(context);
  static DockerRegistryAccount account = new DockerRegistryAccount();
  static DockerRegistryCatalog catalog = new DockerRegistryCatalog();
  static String catalogJson;
  static DockerBearerToken token = new DockerBearerToken();
  static String tokenJson;

  @BeforeAll
  public static void setup() throws JsonProcessingException {
    account.setRepositoriesRegex("library");
    account.setName("my-registry");
    account.setAddress(wmDocker.baseUrl());
    account.setRepositories(List.of("library/nginx"));
    catalog.setRepositories(List.of("library/nginx"));
    ObjectMapper mapper = new ObjectMapper();
    catalogJson = mapper.writeValueAsString(catalog);
    token.setToken("token");
    tokenJson = mapper.writeValueAsString(token);
  }

  @Test
  public void validateDockerRegistryAccount() {
    wmDocker.stubFor(
        WireMock.get(urlEqualTo("/token"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(tokenJson)));

    wmDocker.stubFor(
        WireMock.get(urlEqualTo("/v2/library/nginx/tags/list?n=100"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(401)
                    .withHeader(
                        "WWW-Authenticate",
                        "Bearer realm=\""
                            + wmDocker.baseUrl()
                            + "/token\", service=\"registry.example.com\", scope=\"repository:myrepo/myimage:pull\"")));
    wmDocker.stubFor(
        WireMock.get(urlEqualTo("/v2/library/nginx/tags/list?n=100"))
            .withHeader("Authorization", WireMock.equalTo("Bearer token"))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(200)
                    .withBody(
                        "{\"name\": \"library/nginx\", \"tags\": [\"latest\", \"1.0\", \"1.1\"]}")));

    validator.validate(configProblemSetBuilder, account);
    assertThat(configProblemSetBuilder.build().getProblems().size()).isEqualTo(2);
    Problem problem = configProblemSetBuilder.build().getProblems().get(0);
    assertThat(problem.getMessage())
        .isEqualTo(
            "Unable to fetch tags from the docker repository: library/nginx, Cannot invoke method getService() on null object");
  }

  @Configuration
  public static class TestConfig {

    @Bean
    public HalconfigDirectoryStructure halconfigDirectoryStructure() {
      return new HalconfigDirectoryStructure(".hal");
    }

    @MockBean FileService fileService;

    @Bean
    public DockerRegistryAccountValidator dockerRegistryAccountValidator() {
      return new DockerRegistryAccountValidator();
    }
  }
}
