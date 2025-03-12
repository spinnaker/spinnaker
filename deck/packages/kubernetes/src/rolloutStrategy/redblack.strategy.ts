import type { IDeploymentStrategy } from '@spinnaker/core';

export const strategyRedBlack: IDeploymentStrategy = {
  label: 'Red/Black (Deprecated)',
  description: 'Disables <i>all</i> previous ReplicaSets in the cluster as soon as the new ReplicaSet is ready',
  key: 'redblack',
};
