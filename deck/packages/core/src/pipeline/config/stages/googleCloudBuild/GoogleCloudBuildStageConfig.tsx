import { cloneDeep } from 'lodash';
import React from 'react';

import type { IFormikStageConfigInjectedProps } from '../FormikStageConfig';
import { FormikStageConfig } from '../FormikStageConfig';
import { GoogleCloudBuildStageForm } from './GoogleCloudBuildStageForm';
import type { IGoogleCloudBuildStage } from './IGoogleCloudBuildStage';
import { BuildDefinitionSource } from './IGoogleCloudBuildStage';
import type { IStageConfigProps } from '../common';
import { validate } from './googleCloudBuildValidators';

export function GoogleCloudBuildStageConfig({ application, pipeline, stage, updateStage }: IStageConfigProps) {
  const stageWithDefaults: IGoogleCloudBuildStage = React.useMemo(() => {
    return {
      application: application.name,
      buildDefinitionSource: BuildDefinitionSource.TEXT,
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
      render={(props: IFormikStageConfigInjectedProps) => <GoogleCloudBuildStageForm {...props} />}
    />
  );
}
