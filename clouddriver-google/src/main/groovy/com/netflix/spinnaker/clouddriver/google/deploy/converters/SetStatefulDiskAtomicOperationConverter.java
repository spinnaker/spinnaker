package com.netflix.spinnaker.clouddriver.google.deploy.converters;

import com.netflix.spinnaker.clouddriver.google.GoogleOperation;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory;
import com.netflix.spinnaker.clouddriver.google.deploy.description.SetStatefulDiskDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.ops.SetStatefulDiskAtomicOperation;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@GoogleOperation(AtomicOperations.SET_STATEFUL_DISK)
@Component
public class SetStatefulDiskAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  private final GoogleClusterProvider clusterProvider;
  private final GoogleComputeApiFactory computeApiFactory;

  @Autowired
  public SetStatefulDiskAtomicOperationConverter(
      GoogleClusterProvider clusterProvider, GoogleComputeApiFactory computeApiFactory) {
    this.clusterProvider = clusterProvider;
    this.computeApiFactory = computeApiFactory;
  }

  @Override
  public SetStatefulDiskAtomicOperation convertOperation(Map input) {
    return new SetStatefulDiskAtomicOperation(
        clusterProvider, computeApiFactory, convertDescription(input));
  }

  @Override
  public SetStatefulDiskDescription convertDescription(Map input) {
    return GoogleAtomicOperationConverterHelper.convertDescription(
        input, this, SetStatefulDiskDescription.class);
  }
}
