package com.netflix.spinnaker.rosco.manifests.helm;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import com.netflix.spinnaker.rosco.manifests.TemplateUtils.BakeManifestEnvironment;
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

  public Artifact bake(HelmBakeManifestRequest bakeManifestRequest) {
    BakeManifestEnvironment env = new BakeManifestEnvironment();
    BakeRecipe recipe = helmTemplateUtils.buildBakeRecipe(env, bakeManifestRequest);

    byte[] bakeResult = doBake(env, recipe);
    return Artifact.builder()
        .type("embedded/base64")
        .name(bakeManifestRequest.getOutputArtifactName())
        .reference(Base64.getEncoder().encodeToString(bakeResult))
        .build();
  }
}
