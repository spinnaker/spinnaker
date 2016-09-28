package com.netflix.spinnaker.fiat.roles.github.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.spinnaker.fiat.roles.github.GitHubProperties;
import lombok.Data;
import lombok.Setter;
import lombok.val;
import org.springframework.context.annotation.Bean;
import retrofit.Endpoints;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.SimpleXMLConverter;

import javax.validation.Valid;

/**
 * Wrapper class for a collection of GitHub clients
 */
@Data
public class GitHubMaster {

  @Setter
  private GitHubClient gitHubClient;

  @Setter
  private String baseUrl;

  @Bean
  public GitHubMaster gitHubMasters(@Valid GitHubProperties gitHubProperties) {
    val client = gitHubClient(gitHubProperties.getBaseUrl(),
                              gitHubProperties.getAccessToken());

    GitHubMaster master = new GitHubMaster();
    master.setGitHubClient(client);
    master.setBaseUrl(gitHubProperties.getBaseUrl());

    return master;
  }

  public static GitHubClient gitHubClient(String address, String accessToken) {
    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(address))
        .setRequestInterceptor(new BasicAuthRequestInterceptor(accessToken))
        .setClient(new OkClient())
        .setConverter(new SimpleXMLConverter())
        .build()
        .create(GitHubClient.class);
  }

  @Data
  public static class BasicAuthRequestInterceptor implements RequestInterceptor {
    private final String accessToken;

    public BasicAuthRequestInterceptor(String accessToken) {
      this.accessToken = accessToken;
    }

    @Override
    public void intercept(RequestFacade request) {
      request.addQueryParam("access_token", accessToken);
    }
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TeamMembership {
    private String role;
    private String state;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Team {
    private Long id;
    private String name;
    private String slug;
  }
}
