import { IHealth } from './IHealth';

export interface IInstance {
  id: string;
  availabilityZone?: string;
  account?: string;
  region?: string;
  cloudProvider?: string;
  provider?: string;
  vpcId?: string;
  healthState?: string;
  health: IHealth[];
  launchTime: number;
  loadBalancers?: string[];
  serverGroup?: string;
  zone: string;
  hasHealthStatus?: boolean;
}
