package com.netflix.spinnaker.orca.clouddriver.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskId implements Serializable {
  private String id;
}
