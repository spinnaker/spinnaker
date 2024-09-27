package com.netflix.spinnaker.halyard.config.model.v1.providers.cloudrun;

import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.SecretFile;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.CommonGoogleAccount;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CloudrunAccount extends CommonGoogleAccount {
  private String localRepositoryDirectory;
  @LocalFile @SecretFile private String sshKnownHostsFilePath;
  private boolean sshTrustUnknownHosts;
}
