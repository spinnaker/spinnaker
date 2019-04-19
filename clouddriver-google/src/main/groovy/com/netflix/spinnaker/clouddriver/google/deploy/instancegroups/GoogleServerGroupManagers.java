package com.netflix.spinnaker.clouddriver.google.deploy.instancegroups;

import com.google.api.services.compute.Compute.InstanceGroupManagers;
import com.google.api.services.compute.Compute.RegionInstanceGroupManagers;
import com.google.api.services.compute.model.Operation;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import java.io.IOException;
import java.util.List;

/**
 * A wrapper around {@link InstanceGroupManagers} and {@link RegionInstanceGroupManagers} that
 * performs operations on a specific {@link GoogleServerGroup}.
 */
public interface GoogleServerGroupManagers {

  Operation abandonInstances(List<String> instances) throws IOException;

  Operation delete() throws IOException;

  GoogleServerGroupOperationPoller getOperationPoller();
}
