import * as React from 'react';

import {
  ExecutionDetailsSection,
  IExecutionDetailsSectionProps,
  StageExecutionLogs,
  StageFailureMessage,
} from 'core/pipeline';
import { JsonUtils } from 'core/utils';

export function FindArtifactFromExecutionExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;

  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className="col-md-9">
          <dl className="dl-narrow dl-horizontal">
            <dt>Match Artifact</dt>
            <dd>{JsonUtils.makeSortedStringFromObject(stage.context.expectedArtifact.matchArtifact)}</dd>
            {stage.context.expectedArtifact.useDefaultArtifact && <dt>Default Artifact</dt>}
            {stage.context.expectedArtifact.useDefaultArtifact && (
              <dd>{JsonUtils.makeSortedStringFromObject(stage.context.expectedArtifact.defaultArtifact)}</dd>
            )}
          </dl>
        </div>
      </div>

      <StageFailureMessage stage={stage} message={stage.outputs.exception} />
      <StageExecutionLogs stage={stage} />
    </ExecutionDetailsSection>
  );
}

export namespace FindArtifactFromExecutionExecutionDetails {
  export const title = 'findArtifactFromExecutionConfig';
}
