import type { IEntityTags } from './IEntityTags';
import type { IExecution } from './IExecution';
import type { IInstance } from './IInstance';
import type { IInstanceCounts } from './IInstanceCounts';
import type { IManagedResource } from './IManagedEntity';
import type { ITask } from './ITask';
import type { IMoniker } from '../naming';
import type { ICapacity } from '../serverGroup';

// remnant from legacy code
export interface IAsg {
  minSize: number;
  maxSize: number;
  desiredCapacity: number;
  tags?: any[];
}

export interface IServerGroup extends IManagedResource {
  account: string;
  app?: string;
  asg?: IAsg;
  buildInfo?: any;
  capacity?: ICapacity;
  category?: string;
  cloudProvider: string;
  cluster: string;
  clusterEntityTags?: IEntityTags[];
  createdTime?: number;
  detachedInstances?: IInstance[];
  detail?: string;
  disabledDate?: number;
  entityTags?: IEntityTags;
  insightActions?: Array<{ url: string; label: string }>;
  instanceCounts: IInstanceCounts;
  instances: IInstance[];
  instanceType?: string;
  isDisabled?: boolean;
  labels?: { [key: string]: string };
  launchConfig?: any;
  image?: any;
  loadBalancers?: string[];
  moniker?: IMoniker;
  name: string;
  provider?: string;
  providerMetadata?: any;
  region: string;
  runningExecutions?: IExecution[];
  runningTasks?: ITask[];
  searchField?: string;
  securityGroups?: string[];
  serverGroupManagers?: Array<{ account: string; location: string; name: string }>;
  stack?: string;
  stringVal?: string;
  subnetType?: string;
  tags?: any;
  type: string;
  vpcId?: string;
  vpcName?: string;
}
