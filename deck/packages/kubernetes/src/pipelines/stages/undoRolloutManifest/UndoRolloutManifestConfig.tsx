import { defaults } from 'lodash';
import React, { useEffect } from 'react';

import type { IFormikStageConfigInjectedProps, IStageConfigProps } from '@spinnaker/core';
import { FormikStageConfig } from '@spinnaker/core';

import { UndoRolloutManifestStageForm } from './UndoRolloutManifestStageForm';

export function UndoRolloutManifestConfig({
  application,
  pipeline,
  stage,
  updateStage,
  stageFieldUpdated,
}: IStageConfigProps) {
  useEffect(() => {
    defaults(stage, {
      cloudProvider: 'kubernetes',
    });

    if (stage.isNew) {
      stage.numRevisionsBack = 1;
    }
  }, []);

  return (
    <FormikStageConfig
      application={application}
      pipeline={pipeline}
      stage={stage}
      onChange={updateStage}
      render={(props: IFormikStageConfigInjectedProps) => (
        <UndoRolloutManifestStageForm {...props} stageFieldUpdated={stageFieldUpdated} />
      )}
    />
  );
}
