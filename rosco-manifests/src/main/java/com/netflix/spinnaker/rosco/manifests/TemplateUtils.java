package com.netflix.spinnaker.rosco.manifests;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public abstract class TemplateUtils {
  @Autowired
  ClouddriverService clouddriverService;

  public BakeRecipe buildBakeRecipe(BakeManifestRequest request) {
    BakeRecipe result = new BakeRecipe();
    result.setName(request.getOutputName());
    return result;
  }

  private RetrySupport retrySupport = new RetrySupport();

  private String nameFromReference(String reference) {
    return reference.replace("/", "_")
        .replace(":", "_");
  }

  public Path downloadArtifactToTmpFile(Artifact artifact) throws IOException {
    Path path = Paths.get(System.getProperty("java.io.tmpdir"), nameFromReference(artifact.getReference()));
    OutputStream outputStream = new FileOutputStream(path.toString());

    Response response = retrySupport.retry(() -> clouddriverService.fetchArtifact(artifact), 5, 1000, true);

    InputStream inputStream = response.getBody().in();
    IOUtils.copy(inputStream, outputStream);
    inputStream.close();
    outputStream.close();

    return path;
  }
}
