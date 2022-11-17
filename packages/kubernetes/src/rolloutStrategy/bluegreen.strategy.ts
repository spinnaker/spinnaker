import type { IDeploymentStrategy } from '@spinnaker/core';

export const strategyBlueGreen: IDeploymentStrategy = {
  label: 'Blue/Green',
  description: 'Disables <i>all</i> previous ReplicaSets in the cluster as soon as the new ReplicaSet is ready',
  key: 'bluegreen',
};
