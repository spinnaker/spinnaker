import {Instance} from './instance';
import {InstanceCounts} from './instanceCounts';

export interface ServerGroup {
  account: string;
  app?: string;
  cloudProvider?: string;
  cluster: string;
  instanceCounts: InstanceCounts;
  instances: Instance[];
  launchConfig?: any;
  loadBalancers?: string[];
  name: string;
  provider?: string;
  region: string;
  type: string;
}
