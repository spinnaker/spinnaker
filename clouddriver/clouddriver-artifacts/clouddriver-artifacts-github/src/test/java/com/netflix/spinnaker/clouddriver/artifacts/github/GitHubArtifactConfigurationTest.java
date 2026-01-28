package com.netflix.spinnaker.clouddriver.artifacts.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.config.ArtifactConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties(value = GitHubArtifactProviderProperties.class)
@Import({
  GitHubArtifactConfiguration.class,
  ArtifactConfiguration.class,
  TestConfigurationForArtifacts.class
})
@TestPropertySource(locations = "classpath:github-account-test.properties")
class GitHubArtifactConfigurationTest {
  @Autowired ArtifactCredentialsRepository credentialsRepository;

  @Test
  public void verifyDefaultsGetSet() {
    GitHubArtifactCredentials credentialsForType =
        (GitHubArtifactCredentials)
            credentialsRepository.getCredentialsForType("test", "github/file");
    assertThat(credentialsForType).isNotNull();
    assertThat(credentialsForType.getAccount().getUrlRestrictions().getAllowedSchemes())
        .contains("http", "https");
    assertThat(credentialsForType.getAccount().getUrlRestrictions().getAllowedDomains())
        .contains("github.com");
  }
}
