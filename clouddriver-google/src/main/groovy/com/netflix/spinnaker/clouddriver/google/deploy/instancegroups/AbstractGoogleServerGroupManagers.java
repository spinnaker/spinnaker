package com.netflix.spinnaker.clouddriver.google.deploy.instancegroups;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor;
import com.netflix.spinnaker.clouddriver.google.security.AccountForClient;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.util.List;

public abstract class AbstractGoogleServerGroupManagers implements GoogleServerGroupManagers {

  private final GoogleNamedAccountCredentials credentials;
  private final Registry registry;
  private final String instanceGroupName;

  AbstractGoogleServerGroupManagers(
      GoogleNamedAccountCredentials credentials, Registry registry, String instanceGroupName) {
    this.credentials = credentials;
    this.registry = registry;
    this.instanceGroupName = instanceGroupName;
  }

  @Override
  public Operation abandonInstances(List<String> instances) throws IOException {
    return timeExecute(performAbandonInstances(instances), "abandonInstances");
  }

  abstract ComputeRequest<Operation> performAbandonInstances(List<String> instances)
      throws IOException;

  @Override
  public Operation delete() throws IOException {
    return timeExecute(performDelete(), "delete");
  }

  abstract ComputeRequest<Operation> performDelete() throws IOException;

  @Override
  public InstanceGroupManager get() throws IOException {
    return timeExecute(performGet(), "get");
  }

  abstract ComputeRequest<InstanceGroupManager> performGet() throws IOException;

  @Override
  public Operation update(InstanceGroupManager content) throws IOException {
    return timeExecute(performUpdate(content), "update");
  }

  abstract ComputeRequest<Operation> performUpdate(InstanceGroupManager content) throws IOException;

  private <T> T timeExecute(AbstractGoogleClientRequest<T> request, String api) throws IOException {
    return GoogleExecutor.timeExecute(
        registry,
        request,
        "google.api",
        String.format("compute.%s.%s", getManagersType(), api),
        getTimeExecuteTags(request));
  }

  private String[] getTimeExecuteTags(AbstractGoogleClientRequest<?> request) {
    String account = AccountForClient.getAccount(request.getAbstractGoogleClient());
    return ImmutableList.<String>builder()
        .add("account")
        .add(account)
        .addAll(getRegionOrZoneTags())
        .build()
        .toArray(new String[] {});
  }

  GoogleNamedAccountCredentials getCredentials() {
    return credentials;
  }

  String getProject() {
    return credentials.getProject();
  }

  String getInstanceGroupName() {
    return instanceGroupName;
  }

  abstract String getManagersType();

  abstract List<String> getRegionOrZoneTags();
}
