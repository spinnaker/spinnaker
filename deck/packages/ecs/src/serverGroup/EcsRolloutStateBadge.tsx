import React from 'react';

import { Tooltip } from '@spinnaker/core';

export interface IEcsRolloutStateBadgeProps {
  rolloutState: string;
  rolloutStateReason?: string;
}

/**
 * Compact ECS deployment rollout-state indicator shown in the server-group card header, next to
 * the task-definition revision. ECS reports one of IN_PROGRESS / COMPLETED / FAILED for the
 * service's PRIMARY deployment; this is the only "is the deploy settled?" signal an ecs-native
 * service has, since it has no vNNN sequence and rolls task-def revisions in place.
 */
export const EcsRolloutStateBadge = ({ rolloutState, rolloutStateReason }: IEcsRolloutStateBadgeProps) => {
  if (!rolloutState) {
    return null;
  }

  const normalized = rolloutState.toUpperCase();
  const tooltip = rolloutStateReason
    ? `${rolloutState}: ${rolloutStateReason}`
    : `Rollout ${rolloutState.toLowerCase()}`;

  if (normalized === 'FAILED') {
    return (
      <Tooltip value={tooltip}>
        <span className="ecs-rollout-state ecs-rollout-state--failed">FAILED</span>
      </Tooltip>
    );
  }

  const modifier = normalized === 'COMPLETED' ? 'completed' : 'in-progress';
  return (
    <Tooltip value={tooltip}>
      <span className={`ecs-rollout-state-dot ecs-rollout-state-dot--${modifier}`} aria-label={tooltip} />
    </Tooltip>
  );
};
