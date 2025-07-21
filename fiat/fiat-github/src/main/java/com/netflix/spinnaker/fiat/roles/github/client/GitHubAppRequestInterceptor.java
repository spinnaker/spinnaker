package com.netflix.spinnaker.fiat.roles.github.client;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

@Slf4j
@RequiredArgsConstructor
public class GitHubAppRequestInterceptor implements Interceptor {

  private final GitHubAppAuthService authService;

  @Override
  public @NotNull Response intercept(Chain chain) throws IOException {
    try {
      String installationToken = authService.getInstallationToken();

      Request request =
          chain
              .request()
              .newBuilder()
              .addHeader("Authorization", "Bearer " + installationToken)
              .addHeader("Accept", "application/vnd.github.v3+json")
              .addHeader("User-Agent", "Spinnaker-Fiat")
              .build();

      return chain.proceed(request);
    } catch (Exception e) {
      log.error("Failed to authenticate GitHub App request", e);
      throw new IOException("GitHub App authentication failed", e);
    }
  }
}
