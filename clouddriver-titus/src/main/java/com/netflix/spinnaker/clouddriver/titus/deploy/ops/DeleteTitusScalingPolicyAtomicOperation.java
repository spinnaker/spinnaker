package com.netflix.spinnaker.clouddriver.titus.deploy.ops;

import com.netflix.spinnaker.clouddriver.orchestration.sagas.AbstractSagaAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.SagaAtomicOperationBridge;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.DeleteTitusScalingPolicy;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.DeleteTitusScalingPolicy.DeleteTitusScalingPolicyCommand;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.DeleteTitusScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.TitusExceptionHandler;
import java.util.List;

public class DeleteTitusScalingPolicyAtomicOperation
    extends AbstractSagaAtomicOperation<DeleteTitusScalingPolicyDescription, Object, Void> {

  public DeleteTitusScalingPolicyAtomicOperation(DeleteTitusScalingPolicyDescription description) {
    super(description);
  }

  @Override
  protected SagaFlow buildSagaFlow(List priorOutputs) {
    return new SagaFlow()
        .then(DeleteTitusScalingPolicy.class)
        .exceptionHandler(TitusExceptionHandler.class);
  }

  @Override
  protected void configureSagaBridge(
      SagaAtomicOperationBridge.ApplyCommandWrapper.ApplyCommandWrapperBuilder builder) {
    builder.initialCommand(
        DeleteTitusScalingPolicyCommand.builder().description(description).build());
  }

  @Override
  protected Void parseSagaResult(Object result) {
    return null;
  }
}
