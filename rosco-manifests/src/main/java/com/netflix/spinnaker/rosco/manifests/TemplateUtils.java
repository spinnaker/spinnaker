package com.netflix.spinnaker.rosco.manifests;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.rosco.jobs.BakeRecipe;
import com.netflix.spinnaker.rosco.services.ClouddriverService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import javax.xml.bind.DatatypeConverter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Component
@Slf4j
public abstract class TemplateUtils {
  @Autowired
  ClouddriverService clouddriverService;

  private RetrySupport retrySupport = new RetrySupport();

  private String nameFromReference(String reference) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      return DatatypeConverter.printHexBinary(md.digest(reference.getBytes()));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Failed to save bake manifest: " + e.getMessage(), e);
    }
  }

  protected Path downloadArtifactToTmpFile(BakeManifestEnvironment env, Artifact artifact) throws IOException {
    if (artifact.getReference() == null) {
      throw new InvalidRequestException("Input artifact has an empty 'reference' field.");
    }
    Path path = Paths.get(env.getStagingPath().toString(), nameFromReference(artifact.getReference()));
    OutputStream outputStream = new FileOutputStream(path.toString());

    Response response = retrySupport.retry(() -> clouddriverService.fetchArtifact(artifact), 5, 1000, true);

    if (response.getBody() != null) {
      InputStream inputStream = response.getBody().in();
      IOUtils.copy(inputStream, outputStream);
      inputStream.close();
    }
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
