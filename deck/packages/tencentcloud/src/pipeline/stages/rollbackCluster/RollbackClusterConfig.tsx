import React from 'react';

import type { IStageConfigProps } from '@spinnaker/core';
import { FormikStageConfig } from '@spinnaker/core';

import { RollbackClusterStageForm } from './RollbackClusterStageForm';

export function RollbackClusterConfig({ application, pipeline, stage, updateStage }: IStageConfigProps) {
  return (
    <FormikStageConfig
      application={application}
      onChange={updateStage}
      pipeline={pipeline}
      stage={stage}
      defaults={{
        cloudProvider: 'tencentcloud',
        regions: stage.regions || [],
      }}
      render={(props) => <RollbackClusterStageForm {...props} />}
    />
  );
}
