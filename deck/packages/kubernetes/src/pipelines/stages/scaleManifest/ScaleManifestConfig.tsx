import { defaults } from 'lodash';
import React, { useEffect } from 'react';

import type { IFormikStageConfigInjectedProps, IStageConfigProps } from '@spinnaker/core';
import { FormikStageConfig } from '@spinnaker/core';

import { ScaleManifestStageForm } from './ScaleManifestStageForm';

export function ScaleManifestStageConfig({
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

    if (stage.isNew) {
      stage.replicas = 0;
    }
  }, []);

  return (
    <FormikStageConfig
      application={application}
      pipeline={pipeline}
      stage={stage}
      onChange={updateStage}
      render={(props: IFormikStageConfigInjectedProps) => (
        <ScaleManifestStageForm {...props} stageFieldUpdated={stageFieldUpdated} />
      )}
    />
  );
}
