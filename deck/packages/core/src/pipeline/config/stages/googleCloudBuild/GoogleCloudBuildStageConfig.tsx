import React from 'react';

import type { IFormikStageConfigInjectedProps } from '../FormikStageConfig';
import { FormikStageConfig } from '../FormikStageConfig';
import { GoogleCloudBuildStageForm } from './GoogleCloudBuildStageForm';
import { BuildDefinitionSource } from './IGoogleCloudBuildStage';
import type { IStageConfigProps } from '../common';
import { validate } from './googleCloudBuildValidators';

export function GoogleCloudBuildStageConfig({ application, pipeline, stage, updateStage }: IStageConfigProps) {
  return (
    <FormikStageConfig
      application={application}
      onChange={updateStage}
      pipeline={pipeline}
      stage={stage}
      defaults={{
        application: application.name,
        buildDefinitionSource: BuildDefinitionSource.TEXT,
      }}
      validate={validate}
      render={(props: IFormikStageConfigInjectedProps) => <GoogleCloudBuildStageForm {...props} />}
    />
  );
}
