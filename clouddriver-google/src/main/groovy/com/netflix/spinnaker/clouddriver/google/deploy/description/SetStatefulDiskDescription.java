package com.netflix.spinnaker.clouddriver.google.deploy.description;

import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.resources.CredentialsNameable;
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupNameable;
import lombok.Data;

@Data
public class SetStatefulDiskDescription implements CredentialsNameable, ServerGroupNameable {

  private GoogleNamedAccountCredentials credentials;
  private String serverGroupName;
  private String region;
  private String deviceName;
}
