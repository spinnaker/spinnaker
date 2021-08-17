import { get } from 'lodash';
import React from 'react';

import { IExecutionStageSummary } from '@spinnaker/core';

import { CanaryScore } from './CanaryScore';

export class CanaryExecutionLabel extends React.Component<{ stage: IExecutionStageSummary }, any> {
  public render() {
    const canary: any = get(this.props, 'stage.masterStage.context.canary', {});
    const canaryHealth = canary.health || {};
    const canaryResult = canary.canaryResult || {};
    return (
      <span className="stage-label">
        <span>{this.props.stage.name}</span> (
        <CanaryScore
          inverse={true}
          score={canaryResult.overallScore}
          result={canaryResult.overallResult}
          health={canaryHealth.health}
        />
        )
      </span>
    );
  }
}
