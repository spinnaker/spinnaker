import React from 'react';

import type { IStageConfigProps } from '@spinnaker/core';
import { FormikStageConfig } from '@spinnaker/core';

import { DisableClusterStageForm } from './DisableClusterStageForm';

export function DisableClusterConfig({ application, pipeline, stage, updateStage }: IStageConfigProps) {
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
      render={(props) => <DisableClusterStageForm {...props} />}
    />
  );
}
