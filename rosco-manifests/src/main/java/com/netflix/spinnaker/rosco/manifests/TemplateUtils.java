package com.netflix.spinnaker.rosco.manifests;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

@Component
@Slf4j
public class TemplateUtils {
  private final ClouddriverService clouddriverService;
  private RetrySupport retrySupport = new RetrySupport();

  public TemplateUtils(ClouddriverService clouddriverService) {
    this.clouddriverService = clouddriverService;
  }

  protected void downloadArtifact(Artifact artifact, File targetFile) throws IOException {
    try (OutputStream outputStream = new FileOutputStream(targetFile)) {
      Response response =
          retrySupport.retry(() -> clouddriverService.fetchArtifact(artifact), 5, 1000, true);
      if (response.getBody() != null) {
        try (InputStream inputStream = response.getBody().in()) {
          IOUtils.copy(inputStream, outputStream);
        }
      }
    }
  }

  public static class BakeManifestEnvironment {
    @Getter
    private final Path stagingPath =
        Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());

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
        throw new RuntimeException(
            "Failed to cleanup bake manifest environment: " + e.getMessage(), e);
      }
    }
  }
}
