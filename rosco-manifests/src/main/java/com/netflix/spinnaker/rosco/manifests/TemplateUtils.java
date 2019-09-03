package com.netflix.spinnaker.rosco.manifests;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import lombok.extern.slf4j.Slf4j;
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
}
