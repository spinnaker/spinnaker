import { get } from 'lodash';
import * as React from 'react';

import type { IExecutionStage, IExecutionStageSummary } from '@spinnaker/core';

import { CanaryScore } from '../../components/canaryScore';

export interface ICanaryExecutionLabelProps {
  stage: IExecutionStageSummary;
}

export const CanaryExecutionLabel = ({ stage }: ICanaryExecutionLabelProps) => {
  const { overallScore, overallResult } = get(stage, 'masterStage.context', {}) as IExecutionStage['context'];
  const score = (
    <CanaryScore
      inverse={true}
      score={overallScore}
      result={overallResult === 'success' ? overallResult : null}
      health={overallResult === 'success' ? null : 'unhealthy'}
    />
  );
  return (
    <span className="stage-label">
      <span>{stage.name}</span> ({score})
    </span>
  );
};
