package com.netflix.spinnaker.orca.batch;

import java.util.Collection;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;

public interface StageBuilderProvider {
  Collection<StageBuilder> all();

  StageBuilder wrap(StageDefinitionBuilder stageDefinitionBuilder);
}
