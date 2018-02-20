import * as React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from 'core/pipeline/config/stages/core';
import { StageExecutionLogs, StageFailureMessage } from 'core/pipeline/details';
import { jsonUtilityService } from 'core/utils';

export function FindArtifactFromExecutionExecutionDetails(props: IExecutionDetailsSectionProps) {
  const { stage } = props;

  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <div className="row">
        <div className="col-md-9">
          <dl className="dl-narrow dl-horizontal">
            <dt>Match Artifact</dt>
            <dd>{jsonUtilityService.makeSortedStringFromObject(stage.context.expectedArtifact.matchArtifact)}</dd>
            { stage.context.expectedArtifact.useDefaultArtifact &&
            <dt>Default Artifact</dt> }
            { stage.context.expectedArtifact.useDefaultArtifact &&
            <dd>{jsonUtilityService.makeSortedStringFromObject(stage.context.expectedArtifact.defaultArtifact)}</dd> }
          </dl>
        </div>
      </div>

      <StageFailureMessage stage={stage} message={stage.outputs.exception}/>
      <StageExecutionLogs stage={stage}/>
    </ExecutionDetailsSection>
  );
};

export namespace FindArtifactFromExecutionExecutionDetails {
  export const title = 'findArtifactFromExecutionConfig';
}
