import { cloneDeep } from 'lodash';
import React from 'react';

import type { IFormikStageConfigInjectedProps } from '../FormikStageConfig';
import { FormikStageConfig } from '../FormikStageConfig';
import { UploadArtifactToObjectStorageStageForm } from './UploadArtifactToObjectStorageStageForm';
import type { IStageConfigProps } from '../common';
import type { IStage } from '../../../../domain';
import { validateUploadArtifactToObjectStorageStage } from './uploadArtifactToObjectStorageValidator';

export function UploadArtifactToObjectStorageStageConfig({
  application,
  pipeline,
  stage,
  updateStage,
}: IStageConfigProps) {
  const stageWithDefaults: IStage = React.useMemo(() => {
    return {
      cloudProvider: stage.cloudProvider || 'aws',
      extractArchive: stage.extractArchive ?? true,
      ...cloneDeep(stage),
    };
  }, []);

  return (
    <FormikStageConfig
      application={application}
      onChange={updateStage}
      pipeline={pipeline}
      stage={stageWithDefaults}
      validate={validateUploadArtifactToObjectStorageStage}
      render={(props: IFormikStageConfigInjectedProps) => <UploadArtifactToObjectStorageStageForm {...props} />}
    />
  );
}
