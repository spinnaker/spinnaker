import React from 'react';
import { cloneDeep } from 'lodash';

import { FormikStageConfig, IFormikStageConfigInjectedProps, IStage, IStageConfigProps } from 'core';

import { AwsCodeBuildStageForm } from './AwsCodeBuildStageForm';
import { validate } from './AwsCodeBuildValidator';

export function AwsCodeBuildStageConfig({
  application,
  pipeline,
  stage,
  updatePipeline,
  updateStage,
}: IStageConfigProps) {
  const stageWithDefaults: IStage = React.useMemo(() => {
    return {
      application: application.name,
      ...cloneDeep(stage),
    };
  }, []);

  return (
    <FormikStageConfig
      application={application}
      onChange={updateStage}
      pipeline={pipeline}
      stage={stageWithDefaults}
      validate={validate}
      render={(props: IFormikStageConfigInjectedProps) => (
        <AwsCodeBuildStageForm {...props} updatePipeline={updatePipeline} />
      )}
    />
  );
}
