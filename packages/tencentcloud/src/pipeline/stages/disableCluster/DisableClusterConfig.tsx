import { cloneDeep } from 'lodash';
import React from 'react';

import { FormikStageConfig, IStageConfigProps } from '@spinnaker/core';

import { DisableClusterStageForm } from './DisableClusterStageForm';

export function DisableClusterConfig({ application, pipeline, stage, updateStage }: IStageConfigProps) {
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
      render={(props) => <DisableClusterStageForm {...props} />}
    />
  );
}
