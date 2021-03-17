package com.netflix.spinnaker.orca.clouddriver.model;

import com.netflix.spinnaker.orca.api.operations.OperationsContext.OperationsContextValue;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskId implements Serializable, OperationsContextValue {
  private String id;
}
