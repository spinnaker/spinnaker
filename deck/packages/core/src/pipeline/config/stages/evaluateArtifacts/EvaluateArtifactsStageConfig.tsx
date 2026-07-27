import React from 'react';

import { EvaluateArtifactsStageForm } from './EvaluateArtifactsStageForm';
import { FormikStageConfig } from '../FormikStageConfig';
import type { IStageConfigProps } from '../common';

import './EvaluateArtifactsStage.less';

export const EvaluateArtifactsStageConfig = (props: IStageConfigProps) => {
  if (props.stage.isNew) {
    props.stage.artifactContents = [];
    props.stage.expectedArtifacts = [];
  }
  return (
    <div className="EvaluateArtifactsStageConfig">
      <FormikStageConfig
        {...props}
        onChange={props.updateStage}
        render={(formikProps) => <EvaluateArtifactsStageForm {...formikProps} />}
      />
    </div>
  );
};
