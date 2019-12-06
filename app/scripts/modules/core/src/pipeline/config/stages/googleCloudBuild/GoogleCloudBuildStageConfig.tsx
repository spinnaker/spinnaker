import React from 'react';
import { cloneDeep, defaults } from 'lodash';

import { FormikStageConfig, IFormikStageConfigInjectedProps, IStageConfigProps } from '@spinnaker/core';

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
    return defaults(cloneDeep(stage), {
      application: application.name,
      buildDefinitionSource: BuildDefinitionSource.TEXT,
    });
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
