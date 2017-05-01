import * as React from 'react';
import { get } from 'lodash';

import {CanaryScore} from './CanaryScore';
import {IExecutionStageSummary} from 'core/domain/index';

export class CanaryExecutionLabel extends React.Component<{ stage: IExecutionStageSummary }, any> {
  public render() {
    const canary = get<any>(this.props, 'stage.masterStage.context.canary', {});
    const canaryHealth = canary.health || {};
    const canaryResult = canary.canaryResult || {};
    return (
      <span className="stage-label">
        <span>{this.props.stage.name}</span> (<CanaryScore
            inverse={true}
            score={canaryResult.overallScore}
            result={canaryResult.overallResult}
            health={canaryHealth.health}
        />)
      </span>
    );
  }
}
