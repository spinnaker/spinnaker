package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.orca.api.operations.OperationsContext;
import com.netflix.spinnaker.orca.api.operations.OperationsInput;
import com.netflix.spinnaker.orca.api.operations.OperationsRunner;
import com.netflix.spinnaker.orca.clouddriver.model.KatoOperationsContext;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KatoOperationsRunner implements OperationsRunner {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final KatoService katoService;

  public KatoOperationsRunner(KatoService katoService) {
    this.katoService = katoService;
  }

  @Override
  public OperationsContext run(@Nonnull OperationsInput operationsInput) {
    TaskId taskId =
        operationsInput.hasCloudProvider()
            ? katoService.requestOperations(
                operationsInput.getCloudProvider(), operationsInput.getOperations())
            : katoService.requestOperations(operationsInput.getOperations());

    return KatoOperationsContext.from(taskId, operationsInput.getContextKey());
  }
}
