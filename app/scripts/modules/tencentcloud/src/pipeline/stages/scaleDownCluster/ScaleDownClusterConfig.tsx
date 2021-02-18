import { cloneDeep } from 'lodash';
import React from 'react';

import { FormikStageConfig, IStageConfigProps } from '@spinnaker/core';

import { ScaleDownClusterStageForm } from './ScaleDownClusterStageForm';

export function ScaleDownClusterConfig({ application, pipeline, stage, updateStage }: IStageConfigProps) {
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
      render={(props) => <ScaleDownClusterStageForm {...props} />}
    />
  );
}
