import { IDeploymentStrategy } from 'core/deploymentStrategy';

export const strategyHighlander: IDeploymentStrategy = {
  label: 'Highlander',
  description: 'Destroys <i>all</i> previous ReplicaSets in the cluster as soon as the new ReplicaSet is ready',
  key: 'highlander',
};
