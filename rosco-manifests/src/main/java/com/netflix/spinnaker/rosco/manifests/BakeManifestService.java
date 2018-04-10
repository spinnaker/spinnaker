package com.netflix.spinnaker.rosco.manifests;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.api.BakeStatus;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.jobs.JobRequest;
import com.netflix.spinnaker.rosco.manifests.helm.HelmTemplateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;

@Component
public class BakeManifestService {
  @Autowired
  HelmTemplateUtils helmTemplateUtils;

  @Autowired
  JobExecutor jobExecutor;

  TemplateUtils templateUtils(BakeManifestRequest request) {
    if (request.templateRenderer == null) {
      throw new IllegalArgumentException("The request type must be set (e.g. helm2).");
    }
    switch (request.templateRenderer) {
      case HELM2:
        return helmTemplateUtils;
      default:
        throw new IllegalArgumentException("Request type " + request.templateRenderer + " is not supported.");
    }
  }

  public Artifact bake(BakeManifestRequest request) {
    TemplateUtils utils = templateUtils(request);
    BakeRecipe recipe = utils.buildBakeRecipe(request);

    JobRequest jobRequest = new JobRequest(recipe.getCommand(), new ArrayList<>(), UUID.randomUUID().toString());
    String jobId = jobExecutor.startJob(jobRequest);

    BakeStatus bakeStatus = jobExecutor.updateJob(jobId);

    while (bakeStatus == null || bakeStatus.getState() == BakeStatus.State.RUNNING) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ignored) {
      }

      bakeStatus = jobExecutor.updateJob(jobId);
    }

    if (bakeStatus.getResult() != BakeStatus.Result.SUCCESS) {
      throw new IllegalStateException("Bake of " + request + " failed: " + bakeStatus.getLogsContent());
    }

    return Artifact.builder()
        .type("embedded/base64")
        .name(recipe.getName())
        .reference(Base64.getEncoder().encodeToString(bakeStatus.getLogsContent().getBytes()))
        .build();
  }
}
