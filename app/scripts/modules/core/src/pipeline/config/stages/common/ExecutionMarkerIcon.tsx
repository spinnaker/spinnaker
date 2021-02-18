import { IExecutionStageSummary } from 'core/domain/IExecutionStage';
import React from 'react';

export interface IExecutionMarkerIconProps {
  stage: IExecutionStageSummary;
}

export const ExecutionMarkerIcon = ({ stage }: IExecutionMarkerIconProps) =>
  stage.isSuspended || stage.suspendedStageTypes.size > 0 ? <span className="far fa-clock sp-margin-xs-right" /> : null;
