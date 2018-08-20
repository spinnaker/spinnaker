import { IEntityTags } from './IEntityTags';
import { IExecution } from './IExecution';
import { IInstance } from './IInstance';
import { IInstanceCounts } from './IInstanceCounts';
import { ITask } from './ITask';
import { IMoniker } from 'core/naming/IMoniker';
import { ICapacity } from 'core/serverGroup';

// remnant from legacy code
export interface IAsg {
  minSize: number;
  maxSize: number;
  desiredCapacity: number;
  tags?: any[];
}

export interface IServerGroup {
  account: string;
  app?: string;
  asg?: IAsg;
  buildInfo?: any;
  category?: string;
  capacity?: ICapacity;
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
  launchConfig?: any;
  loadBalancers?: string[];
  name: string;
  namespace?: string;
  moniker?: IMoniker;
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
