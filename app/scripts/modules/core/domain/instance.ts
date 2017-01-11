import { Health } from './health';

export class Instance {
  id: string;
  availabilityZone?: string;
  account?: string;
  region?: string;
  cloudProvider?: string;
  healthState?: string;
  health: Health[];
  launchTime: number;
  loadBalancers?: string[];
  serverGroup?: string;
  zone: string;
}
