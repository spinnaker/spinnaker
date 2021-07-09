import React from 'react';

import { IStageConfigProps, StageConfigField } from '../common';
import { IArtifact, IExpectedArtifact, StageArtifactSelector } from '../../../../index';

export const SavePipelinesStageConfig: React.SFC<IStageConfigProps> = (props) => {
  const { stage, pipeline } = props;
  return (
    <div className="container-fluid form-horizontal">
      <StageConfigField label="Expected Artifact" fieldColumns={8}>
        <StageArtifactSelector
          pipeline={pipeline}
          stage={stage}
          expectedArtifactId={props.stage.pipelinesArtifactId}
          artifact={props.stage.pipelinesArtifact}
          onArtifactEdited={(artifact: IArtifact) => {
            props.updateStageField({ pipelinesArtifact: artifact });
            props.updateStageField({ pipelinesArtifactId: null });
          }}
          onExpectedArtifactSelected={(expectedArtifact: IExpectedArtifact) => {
            props.updateStageField({ pipelinesArtifactId: expectedArtifact.id });
            props.updateStageField({ pipelinesArtifact: null });
          }}
        />
      </StageConfigField>
    </div>
  );
};
