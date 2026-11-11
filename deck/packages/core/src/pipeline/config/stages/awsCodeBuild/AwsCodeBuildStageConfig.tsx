import React from 'react';

import { AwsCodeBuildStageForm } from './AwsCodeBuildStageForm';
import { validate } from './AwsCodeBuildValidator';
import type { IFormikStageConfigInjectedProps } from '../FormikStageConfig';
import { FormikStageConfig } from '../FormikStageConfig';
import type { IStageConfigProps } from '../common';

export function AwsCodeBuildStageConfig({ application, pipeline, stage, updateStage }: IStageConfigProps) {
  return (
    <FormikStageConfig
      application={application}
      onChange={updateStage}
      pipeline={pipeline}
      stage={stage}
      defaults={{
        application: application.name,
      }}
      validate={validate}
      render={(props: IFormikStageConfigInjectedProps) => <AwsCodeBuildStageForm {...props} />}
    />
  );
}
