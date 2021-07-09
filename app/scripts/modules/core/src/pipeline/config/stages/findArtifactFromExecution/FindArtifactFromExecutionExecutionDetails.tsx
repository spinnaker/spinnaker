import React from 'react';

import { ExecutionDetailsSection, IExecutionDetailsSectionProps } from '../common';
import { StageExecutionLogs, StageFailureMessage } from '../../../details';
import { JsonUtils } from '../../../../utils';

export class FindArtifactFromExecutionExecutionDetails extends React.Component<IExecutionDetailsSectionProps> {
  public static title = 'artifactDetails';
  public render() {
    const { current, name, stage } = this.props;

    // Prior versions of this stage accepted only one expected artifact.
    const expectedArtifacts = Array.isArray(stage.context.expectedArtifacts)
      ? stage.context.expectedArtifacts
      : [stage.context.expectedArtifact];

    return (
      <ExecutionDetailsSection name={name} current={current}>
        <div className="row">
          {expectedArtifacts.map((expectedArtifact) => (
            <div key={expectedArtifact.id}>
              <h5>{expectedArtifact.displayName}</h5>
              <div>Match Artifact</div>
              <pre>{JsonUtils.makeSortedStringFromObject(expectedArtifact.matchArtifact)}</pre>
              {expectedArtifact.useDefaultArtifact && (
                <>
                  <div>Default Artifact</div>
                  <pre>{JsonUtils.makeSortedStringFromObject(expectedArtifact.defaultArtifact)}</pre>
                </>
              )}
            </div>
          ))}
        </div>
        <StageFailureMessage stage={stage} message={stage.outputs.exception} />
        <StageExecutionLogs stage={stage} />
      </ExecutionDetailsSection>
    );
  }
}
