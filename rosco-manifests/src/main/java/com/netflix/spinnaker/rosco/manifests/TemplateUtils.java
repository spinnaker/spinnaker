package com.netflix.spinnaker.rosco.manifests;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
@Slf4j
public abstract class TemplateUtils {
  @Autowired
  ClouddriverService clouddriverService;

  public BakeRecipe buildBakeRecipe(BakeManifestEnvironment env, BakeManifestRequest request) {
    BakeRecipe result = new BakeRecipe();
    result.setName(request.getOutputName());
    return result;
  }

  private RetrySupport retrySupport = new RetrySupport();

  private String nameFromReference(String reference) {
    return reference.replace("/", "_")
        .replace(":", "_");
  }

  protected Path downloadArtifactToTmpFile(BakeManifestEnvironment env, Artifact artifact) throws IOException {
    Path path = Paths.get(env.getStagingPath().toString(), nameFromReference(artifact.getReference()));
    OutputStream outputStream = new FileOutputStream(path.toString());

    Response response = retrySupport.retry(() -> clouddriverService.fetchArtifact(artifact), 5, 1000, true);

    InputStream inputStream = response.getBody().in();
    IOUtils.copy(inputStream, outputStream);
    inputStream.close();
    outputStream.close();

    return path;
  }

  public static class BakeManifestEnvironment {
    @Getter
    final private Path stagingPath = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());

    public BakeManifestEnvironment() {
      boolean success = stagingPath.toFile().mkdirs();
      if (!success) {
        log.warn("Failed to make directory " + stagingPath + "...");
      }
    }

    public void cleanup() {
      try {
        FileUtils.deleteDirectory(stagingPath.toFile());
      } catch (IOException e) {
        throw new RuntimeException("Failed to cleanup bake manifest environment: " + e.getMessage(), e);
      }
    }
  }
}
