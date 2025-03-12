import { defaults } from 'lodash';
import { useEffect } from 'react';
import React from 'react';

import type { IFormikStageConfigInjectedProps, IStageConfigProps } from '@spinnaker/core';
import { FormikStageConfig } from '@spinnaker/core';

import { FindArtifactsFromResourceStageForm } from './FindArtifactsFromResourceStageForm';

export function FindArtifactsFromResourceConfig({
  application,
  pipeline,
  stage,
  updateStage,
  stageFieldUpdated,
}: IStageConfigProps) {
  useEffect(() => {
    defaults(stage, {
      app: application.name,
      cloudProvider: 'kubernetes',
    });
  }, []);

  return (
    <FormikStageConfig
      application={application}
      pipeline={pipeline}
      stage={stage}
      onChange={updateStage}
      render={(props: IFormikStageConfigInjectedProps) => (
        <FindArtifactsFromResourceStageForm {...props} stageFieldUpdated={stageFieldUpdated} />
      )}
    />
  );
}
