import { IHealth } from './IHealth';

export interface IInstance {
  account?: string;
  availabilityZone?: string;
  cloudProvider?: string;
  cluster?: string;
  hasHealthStatus?: boolean;
  health: IHealth[];
  healthState?: string;
  id: string;
  launchTime: number;
  loadBalancers?: string[];
  name: string;
  provider?: string;
  region?: string;
  serverGroup?: string;
  vpcId?: string;
  zone: string;
}
