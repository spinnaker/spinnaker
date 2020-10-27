

import { API } from 'core/api';

export interface IDeploymentMonitorDefinition {
  id: string;
  name: string;
  supportContact: string;
}

export class DeploymentMonitorReader {
  public static getDeploymentMonitors(): PromiseLike<IDeploymentMonitorDefinition[]> {
    return API.all('capabilities').all('deploymentMonitors').useCache(true).get();
  }
}
