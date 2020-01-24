import React from 'react';
import { cloneDeep } from 'lodash';

import { FormikStageConfig, IFormikStageConfigInjectedProps, IStageConfigProps } from 'core';

import { GoogleCloudBuildStageForm } from './GoogleCloudBuildStageForm';
import { validate } from './googleCloudBuildValidators';
import { BuildDefinitionSource, IGoogleCloudBuildStage } from './IGoogleCloudBuildStage';

export function GoogleCloudBuildStageConfig({
  application,
  pipeline,
  stage,
  updatePipeline,
  updateStage,
}: IStageConfigProps) {
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
      render={(props: IFormikStageConfigInjectedProps) => (
        <GoogleCloudBuildStageForm {...props} updatePipeline={updatePipeline} />
      )}
    />
  );
}
