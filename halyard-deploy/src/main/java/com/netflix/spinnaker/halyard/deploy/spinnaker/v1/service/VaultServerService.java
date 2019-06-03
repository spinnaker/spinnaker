/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import com.google.gson.annotations.SerializedName;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.VaultConfigMount;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.VaultConfigMountSet;
import com.squareup.okhttp.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;
import retrofit.http.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public abstract class VaultServerService extends SpinnakerService<VaultServerService.Vault> {
  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.VAULT;
  }

  @Override
  public Type getType() {
    return Type.VAULT_SERVER;
  }

  @Override
  public Class<Vault> getEndpointClass() {
    return Vault.class;
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    return new ArrayList<>();
  }

  @Autowired HalconfigDirectoryStructure halconfigDirectoryStructure;

  public static String getSpinnakerSecretName(String secretName) {
    return String.join("/", "spinnaker", secretName);
  }

  public interface Vault {
    @GET("/v1/sys/init")
    InitStatus initStatus();

    @PUT("/v1/sys/init")
    InitResponse init(@Body InitRequest initRequest);

    @GET("/v1/sys/seal-status")
    SealStatus sealStatus();

    @PUT("/v1/sys/unseal")
    SealStatus unseal(@Body UnsealRequest unsealRequest);

    @PUT("/v1/secret/{secretName}")
    @Headers({"Content-type: application/json"})
    Response putSecret(
        @Header("X-Vault-Token") String token,
        @Path(value = "secretName", encode = false) String secretName,
        @Body Object contents);
  }

  @Data
  protected static class SealStatus {
    @SerializedName("sealed")
    boolean sealed;
  }

  @Data
  protected static class UnsealRequest {
    @SerializedName("key")
    String key;
  }

  @Data
  protected static class InitStatus {
    @SerializedName("initialized")
    boolean initialized;
  }

  @Data
  protected static class InitResponse {
    @SerializedName("root_token")
    String rootToken;

    @SerializedName("keys")
    List<String> keys = new ArrayList<>();

    @SerializedName("recovery_keys")
    List<String> recoveryKeys = new ArrayList<>();
  }

  @Data
  protected static class InitRequest {
    @SerializedName("secret_shares")
    int secretShares;

    @SerializedName("secret_threshold")
    int secretThreshold;
  }

  @Data
  protected static class VaultError {
    List<String> errors = new ArrayList<>();
  }

  public String writeVaultConfig(
      String deploymentName, Vault vault, String secretName, VaultConfigMount configMount) {
    secretName = getSpinnakerSecretName(secretName);
    writeSecret(deploymentName, vault, secretName, configMount);
    return secretName;
  }

  public String writeVaultConfigMountSet(
      String deploymentName, Vault vault, String secretName, VaultConfigMountSet configMountSet) {
    secretName = getSpinnakerSecretName(secretName);
    writeSecret(deploymentName, vault, secretName, configMountSet);
    return secretName;
  }

  private void writeSecret(String deploymentName, Vault vault, String secretName, Object secret) {
    String token = getToken(deploymentName, vault);
    vault.putSecret(token, secretName, secret);
  }

  public String getToken(String deploymentName, Vault vault) {
    String result = "";
    InitStatus initStatus;

    try {
      initStatus = vault.initStatus();
    } catch (RetrofitError e) {
      throw handleVaultError(e, "check init status");
    }

    if (!initStatus.isInitialized()) {
      try {
        InitResponse init = vault.init(new InitRequest().setSecretShares(3).setSecretThreshold(3));
        result = init.getRootToken();

        for (String key : init.getKeys()) {
          vault.unseal(new UnsealRequest().setKey(key));
        }
      } catch (RetrofitError e) {
        throw handleVaultError(e, "init vault");
      }
    }

    SealStatus sealStatus;
    try {
      sealStatus = vault.sealStatus();
    } catch (RetrofitError e) {
      throw handleVaultError(e, "check seal status");
    }

    if (sealStatus.isSealed()) {
      throw new HalException(
          Problem.Severity.FATAL, "Your vault is in a sealed state, no config can be written.");
    }

    File vaultTokenFile = halconfigDirectoryStructure.getVaultTokenPath(deploymentName).toFile();
    if (result.isEmpty()) {
      try {
        result = IOUtils.toString(new FileInputStream(vaultTokenFile));
      } catch (IOException e) {
        throw new HalException(
            new ProblemBuilder(
                    Problem.Severity.FATAL, "Unable to read vault token: " + e.getMessage())
                .setRemediation(
                    "This file is needed for storing credentials to your vault server. "
                        + "If you have deployed vault by hand, make sure Halyard can authenticate using the token in that file.")
                .build());
      }
    } else {
      try {
        IOUtils.write(result.getBytes(), new FileOutputStream(vaultTokenFile));
      } catch (IOException e) {
        throw new HalException(
            Problem.Severity.FATAL, "Unable to write vault token: " + e.getMessage());
      }
    }

    return result;
  }

  private HalException handleVaultError(RetrofitError e, String operation) {
    if (e.getResponse() != null && e.getResponse().getStatus() == 400) {
      VaultError ve = (VaultError) e.getBodyAs(VaultError.class);
      return new HalException(Problem.Severity.FATAL, "Vault is in an invalid state: " + ve, e);
    } else {
      return new HalException(
          Problem.Severity.FATAL, "Error reaching vault during operation \"" + operation + "\"", e);
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class Settings extends ServiceSettings {
    Integer port = 8200;

    Boolean enabled = true;
    Boolean safeToUpdate = false;
    Boolean monitored = false;
    Boolean sidecar = true;
    Integer targetSize = 1;
    Boolean skipLifeCycleManagement = false;
    Map<String, String> env = new HashMap<>();

    public Settings() {}
  }

  @Override
  protected Optional<String> customProfileOutputPath(String profileName) {
    return Optional.empty();
  }
}
