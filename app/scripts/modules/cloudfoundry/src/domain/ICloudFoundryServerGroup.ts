import { IServerGroup } from '@spinnaker/core';

import { ICloudFoundrySpace, ICloudFoundryDroplet } from 'cloudfoundry/domain';

export interface ICloudFoundryServerGroup extends IServerGroup {
  appsManagerUri: string;
  memory: number;
  diskQuota: number;
  state: 'STARTED' | 'STOPPED';
  space: ICloudFoundrySpace;
  droplet: ICloudFoundryDroplet;
  serviceInstances: ICloudFoundryServiceInstance[];
  env: ICloudFoundryEnvVar[];
}

export interface ICloudFoundryServiceInstance {
  name: string;
  plan: string;
  service: string;
}

export interface ICloudFoundryEnvVar {
  key: string;
  value: string;
}
