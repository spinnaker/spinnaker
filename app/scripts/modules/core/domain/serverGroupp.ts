import { Instance } from './instance';
import { InstanceCounts } from './instanceCounts';

export interface ServerGroup {
  account:string;
  cluster: string;
  instanceCounts: InstanceCounts;
  instances: Instance[];
  loadBalancers?: string[];
  name: string;
  region:string;
  type: string;
}
