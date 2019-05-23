package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.Compute.InstanceGroupManagers;
import com.google.api.services.compute.Compute.RegionInstanceGroupManagers;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import java.io.IOException;
import java.util.List;

/**
 * A wrapper around {@link InstanceGroupManagers} and {@link RegionInstanceGroupManagers} that
 * performs operations on a specific {@link GoogleServerGroup}.
 */
public interface GoogleServerGroupManagers {

  WaitableComputeOperation abandonInstances(List<String> instances) throws IOException;

  WaitableComputeOperation delete() throws IOException;

  InstanceGroupManager get() throws IOException;

  WaitableComputeOperation update(InstanceGroupManager content) throws IOException;
}
