import { IPromise } from 'angular';

import { API } from 'core/api';

export interface IDeploymentMonitorDefinition {
  id: string;
  name: string;
  supportContact: string;
}

export class DeploymentMonitorReader {
  public static getDeploymentMonitors(): IPromise<IDeploymentMonitorDefinition[]> {
    return API.all('capabilities').all('deploymentMonitors').useCache(true).get();
  }
}
