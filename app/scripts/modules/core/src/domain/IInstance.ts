import { IHealth } from './IHealth';

export interface IInstance {
  account?: string;
  availabilityZone?: string;
  cloudProvider?: string;
  hasHealthStatus?: boolean;
  health: IHealth[];
  healthState?: string;
  id: string;
  launchTime: number;
  loadBalancers?: string[];
  provider?: string;
  region?: string;
  serverGroup?: string;
  vpcId?: string;
  zone: string;
}
