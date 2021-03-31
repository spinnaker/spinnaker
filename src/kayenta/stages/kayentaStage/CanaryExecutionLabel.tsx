import { CanaryScore } from 'kayenta/components/canaryScore';
import { get } from 'lodash';
import * as React from 'react';

import { IExecutionStage, IExecutionStageSummary } from '@spinnaker/core';

export interface ICanaryExecutionLabelProps {
  stage: IExecutionStageSummary;
}

export const CanaryExecutionLabel = ({ stage }: ICanaryExecutionLabelProps) => {
  const { overallScore, overallResult } = get<IExecutionStage['context']>(stage, 'masterStage.context', {});
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
