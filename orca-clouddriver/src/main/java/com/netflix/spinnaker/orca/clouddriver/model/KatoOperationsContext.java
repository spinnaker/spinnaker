package com.netflix.spinnaker.orca.clouddriver.model;

import com.netflix.spinnaker.orca.api.operations.OperationsContext;
import javax.annotation.Nonnull;

public class KatoOperationsContext implements OperationsContext {

  private String contextKey;
  private TaskId contextValue;

  public static KatoOperationsContext from(TaskId taskId, String resultKey) {
    if (resultKey != null && !resultKey.isEmpty()) {
      return new KatoOperationsContext(taskId, resultKey);
    }

    return new KatoOperationsContext(taskId);
  }

  public KatoOperationsContext(TaskId taskId) {
    this.contextValue = taskId;
  }

  public KatoOperationsContext(TaskId taskId, String contextKey) {
    this.contextValue = taskId;
    this.contextKey = contextKey;
  }

  @Nonnull
  @Override
  public String contextKey() {
    return contextKey != null ? contextKey : "kato.last.task.id";
  }

  @Nonnull
  @Override
  public OperationsContextValue contextValue() {
    return contextValue;
  }
}
