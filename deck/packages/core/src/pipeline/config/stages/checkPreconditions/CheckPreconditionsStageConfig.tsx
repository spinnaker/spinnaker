import React from 'react';

import type { IStageConfigProps } from '../common';
import { PreconditionList } from '../../preconditions/PreconditionList';
import { PipelineConfigService } from '../../services/PipelineConfigService';

export function CheckPreconditionsStageConfig(props: IStageConfigProps) {
  const { application, pipeline, stage, updateStageField } = props;
  const preconditions = stage.preconditions || [];

  return (
    <PreconditionList
      application={application}
      onChange={(preconditions) => updateStageField({ preconditions })}
      preconditions={preconditions}
      strategy={pipeline.strategy}
      upstreamStages={PipelineConfigService.getAllUpstreamDependencies(pipeline, stage)}
    />
  );
}
