import { cloneDeep } from 'lodash';
import React from 'react';

import { AwsCodeBuildStageForm } from './AwsCodeBuildStageForm';
import { validate } from './AwsCodeBuildValidator';
import type { IFormikStageConfigInjectedProps } from '../FormikStageConfig';
import { FormikStageConfig } from '../FormikStageConfig';
import type { IStageConfigProps } from '../common';
import type { IStage } from '../../../../domain';

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
