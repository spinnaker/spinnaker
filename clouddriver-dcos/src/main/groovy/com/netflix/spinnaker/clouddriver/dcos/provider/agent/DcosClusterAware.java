package com.netflix.spinnaker.clouddriver.dcos.provider.agent;

import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials;

import java.util.Collection;

public interface DcosClusterAware {
  String getClusterName();

  String getServiceAccountUID();

  Collection<DcosAccountCredentials> getAccounts();
}
