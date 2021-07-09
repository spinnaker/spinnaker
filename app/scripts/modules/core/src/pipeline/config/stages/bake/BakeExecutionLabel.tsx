import React from 'react';

import { IExecutionStageSummary } from '../../../../domain';

export class BakeExecutionLabel extends React.Component<{ stage: IExecutionStageSummary }> {
  public render() {
    return (
      <span className="stage-label">
        {this.props.stage.name}
        {this.props.stage.masterStage.context.allPreviouslyBaked && (
          <span className="small">
            <br />
            (previously baked)
          </span>
        )}
        {this.props.stage.masterStage.context.somePreviouslyBaked && (
          <span className="small">
            <br />
            (some previously baked)
          </span>
        )}
      </span>
    );
  }
}
