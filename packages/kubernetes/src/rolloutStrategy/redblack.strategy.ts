import { IDeploymentStrategy } from '@spinnaker/core';

export const strategyRedBlack: IDeploymentStrategy = {
  label: 'Red/Black',
  description: 'Disables <i>all</i> previous ReplicaSets in the cluster as soon as the new ReplicaSet is ready',
  key: 'redblack',
};
