package com.netflix.spinnaker.orca.igor;

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScmService {
  public List compareCommits(
      String repoType,
      String projectKey,
      String repositorySlug,
      Map<String, String> requestParams) {
    return Retrofit2SyncCall.execute(
        igorService.compareCommits(repoType, projectKey, repositorySlug, requestParams));
  }

  public Map<String, Object> getDeliveryConfigManifest(
      String repoType,
      String projectKey,
      String repositorySlug,
      @Nullable String directory,
      @Nullable String manifest,
      @Nullable String ref) {
    return Retrofit2SyncCall.execute(
        igorService.getDeliveryConfigManifest(
            repoType, projectKey, repositorySlug, directory, manifest, ref));
  }

  private final IgorService igorService;
}
