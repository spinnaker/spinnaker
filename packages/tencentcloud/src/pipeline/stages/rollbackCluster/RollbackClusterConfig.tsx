import { cloneDeep } from 'lodash';
import React from 'react';

import type { IStageConfigProps } from '@spinnaker/core';
import { FormikStageConfig } from '@spinnaker/core';

import { RollbackClusterStageForm } from './RollbackClusterStageForm';

export function RollbackClusterConfig({ application, pipeline, stage, updateStage }: IStageConfigProps) {
  const stageWithDefaults = React.useMemo(() => {
    return {
      cloudProvider: 'tencentcloud',
      regions: stage.regions || [],
      ...cloneDeep(stage),
    };
  }, []);

  return (
    <FormikStageConfig
      application={application}
      onChange={updateStage}
      pipeline={pipeline}
      stage={stageWithDefaults}
      render={(props) => <RollbackClusterStageForm {...props} />}
    />
  );
}
