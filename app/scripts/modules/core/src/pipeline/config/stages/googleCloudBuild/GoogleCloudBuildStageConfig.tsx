import { FormikStageConfig, IFormikStageConfigInjectedProps, IStageConfigProps } from 'core';
import { cloneDeep } from 'lodash';
import React from 'react';

import { GoogleCloudBuildStageForm } from './GoogleCloudBuildStageForm';
import { BuildDefinitionSource, IGoogleCloudBuildStage } from './IGoogleCloudBuildStage';
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
