import { cloneDeep } from 'lodash';
import React from 'react';

import { IStage } from '../../../../domain';
import { IStageConfigProps } from '../common';
import { AwsCodeBuildStageForm } from './AwsCodeBuildStageForm';
import { validate } from './AwsCodeBuildValidator';
import { FormikStageConfig, IFormikStageConfigInjectedProps } from '../FormikStageConfig';

export function AwsCodeBuildStageConfig({ application, pipeline, stage, updateStage }: IStageConfigProps) {
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
      render={(props: IFormikStageConfigInjectedProps) => <AwsCodeBuildStageForm {...props} />}
    />
  );
}
