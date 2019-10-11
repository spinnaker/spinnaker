package com.netflix.spinnaker.rosco.manifests.helm;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.manifests.BakeManifestEnvironment;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import java.io.IOException;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class HelmBakeManifestService extends BakeManifestService<HelmBakeManifestRequest> {
  private final HelmTemplateUtils helmTemplateUtils;
  private static final String HELM_TYPE = "HELM2";

  public HelmBakeManifestService(HelmTemplateUtils helmTemplateUtils, JobExecutor jobExecutor) {
    super(jobExecutor);
    this.helmTemplateUtils = helmTemplateUtils;
  }

  @Override
  public Class<HelmBakeManifestRequest> requestType() {
    return HelmBakeManifestRequest.class;
  }

  @Override
  public boolean handles(String type) {
    return type.toUpperCase().equals(HELM_TYPE);
  }

  public Artifact bake(HelmBakeManifestRequest bakeManifestRequest) throws IOException {
    try (BakeManifestEnvironment env = BakeManifestEnvironment.create()) {
      BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);

      String bakeResult = helmTemplateUtils.removeTestsDirectoryTemplates(doBake(recipe));
      return Artifact.builder()
          .type("embedded/base64")
          .name(bakeManifestRequest.getOutputArtifactName())
          .reference(Base64.getEncoder().encodeToString(bakeResult.getBytes()))
          .build();
    }
  }
}
