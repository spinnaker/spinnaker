package com.netflix.spinnaker.orca.api.operations;

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class OperationsInput {

  /** The cloud provider (AWS, Kubernetes, Titus, etc) if the operation is a cloud operation. */
  @Nullable private String cloudProvider;

  /** The operations collection. */
  @Nonnull private Collection<? extends Map<String, Map>> operations;

  /** The {@link StageExecution} that runs this operation. */
  @Nonnull private StageExecution stageExecution;

  /**
   * The context key is passed to {@link OperationsContext#contextKey()} and used to identify the
   * corresponding {@link OperationsContext#contextValue()}.
   */
  @Nullable private String contextKey;

  public boolean hasCloudProvider() {
    return this.cloudProvider != null && !this.cloudProvider.isEmpty();
  }

  public static OperationsInput of(
      String cloudProvider,
      Collection<? extends Map<String, Map>> operations,
      StageExecution stageExecution) {
    return builder()
        .cloudProvider(cloudProvider)
        .operations(operations)
        .stageExecution(stageExecution)
        .build();
  }

  public static OperationsInput of(
      Collection<? extends Map<String, Map>> operations, StageExecution stageExecution) {
    return of(null, operations, stageExecution);
  }
}
