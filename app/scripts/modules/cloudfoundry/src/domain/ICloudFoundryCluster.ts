import { ICloudFoundryServerGroup } from 'cloudfoundry/domain';

export interface ICloudFoundryCluster {
  name: string;
  serverGroups: ICloudFoundryServerGroup[];
}
