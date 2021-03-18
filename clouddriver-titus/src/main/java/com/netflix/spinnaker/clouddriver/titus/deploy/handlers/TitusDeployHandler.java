package com.netflix.spinnaker.clouddriver.titus.deploy.handlers;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler;
import com.netflix.spinnaker.clouddriver.orchestration.events.CreateServerGroupEvent;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.LoadFront50App;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.LoadFront50App.LoadFront50AppCommand;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.SagaAtomicOperationBridge;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.SagaAtomicOperationBridge.ApplyCommandWrapper;
import com.netflix.spinnaker.clouddriver.saga.SagaService;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaFlow;
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.AttachTitusServiceLoadBalancers;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.CopyTitusServiceScalingPolicies;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.PrepareTitusDeploy;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.PrepareTitusDeploy.PrepareTitusDeployCommand;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.SubmitTitusJob;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.TitusServiceJobPredicate;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import groovy.util.logging.Slf4j;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TitusDeployHandler implements DeployHandler<TitusDeployDescription> {
  private final SagaService sagaService;

  @Autowired
  public TitusDeployHandler(SagaService sagaService) {
    this.sagaService = sagaService;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public TitusDeploymentResult handle(
      final TitusDeployDescription inputDescription, List priorOutputs) {
    Objects.requireNonNull(inputDescription.getSagaContext(), "A saga context must be provided");

    SagaFlow flow =
        new SagaFlow()
            .then(LoadFront50App.class)
            .then(PrepareTitusDeploy.class)
            .then(SubmitTitusJob.class)
            .on(
                TitusServiceJobPredicate.class,
                sagaFlow -> {
                  sagaFlow
                      .then(AttachTitusServiceLoadBalancers.class)
                      .then(CopyTitusServiceScalingPolicies.class);
                })
            .exceptionHandler(TitusExceptionHandler.class)
            .completionHandler(TitusDeployCompletionHandler.class);

    final TitusDeploymentResult result =
        new SagaAtomicOperationBridge(sagaService, inputDescription.getSagaContext().getSagaId())
            .apply(
                ApplyCommandWrapper.builder()
                    .sagaName(TitusDeployHandler.class.getSimpleName())
                    .inputDescription(inputDescription)
                    .priorOutputs(priorOutputs)
                    .sagaContext(inputDescription.getSagaContext())
                    .task(getTask())
                    .sagaFlow(flow)
                    .initialCommand(
                        LoadFront50AppCommand.builder()
                            .appName(inputDescription.getApplication())
                            .nextCommand(
                                PrepareTitusDeployCommand.builder()
                                    .description(inputDescription)
                                    .build())
                            .allowMissing(true)
                            .build())
                    .build());

    if (result == null) {
      // "This should never happen"
      throw new TitusException("Failed to complete Titus deploy: No deployment result created");
    }

    // TODO(rz): Ew, side effects...
    result
        .getServerGroupNames()
        .forEach(
            serverGroupName ->
                inputDescription
                    .getEvents()
                    .add(
                        new CreateServerGroupEvent(
                            TitusCloudProvider.ID,
                            result.getTitusAccountId(),
                            inputDescription.getRegion(),
                            serverGroupName)));

    return result;
  }

  @Override
  public boolean handles(DeployDescription description) {
    return description instanceof TitusDeployDescription;
  }
}
