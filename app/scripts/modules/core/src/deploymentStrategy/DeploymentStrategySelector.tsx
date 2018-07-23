import { IServerGroupCommand } from 'core/serverGroup';

import { IDeploymentStrategy } from './deploymentStrategy.registry';

export interface IDeploymentStrategySelectorProps {
  command: IServerGroupCommand;
  onStrategyChange: (command: IServerGroupCommand, strategy: IDeploymentStrategy) => void;
  labelColumns?: string;
  fieldColumns?: string;
}
