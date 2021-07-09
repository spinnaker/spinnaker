import { REST } from '../../../../api';

export interface IDeploymentMonitorDefinition {
  id: string;
  name: string;
  supportContact: string;
}

export class DeploymentMonitorReader {
  public static getDeploymentMonitors(): PromiseLike<IDeploymentMonitorDefinition[]> {
    return REST('/capabilities/deploymentMonitors').useCache(true).get();
  }
}
