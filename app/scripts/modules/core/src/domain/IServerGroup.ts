import { IEntityTags } from './IEntityTags';
import { IExecution } from './IExecution';
import { IInstance } from './IInstance';
import { IInstanceCounts } from './IInstanceCounts';
import { ITask } from './ITask';

// remnant from legacy code
export interface IAsg {
  minSize: number;
  maxSize: number;
  desiredCapacity: number;
}

export interface IServerGroup {
  account: string;
  app?: string;
  asg?: IAsg;
  buildInfo?: any;
  category?: string;
  cloudProvider: string;
  cluster: string;
  clusterEntityTags?: IEntityTags[];
  detail?: string;
  detachedInstances?: IInstance[];
  entityTags?: IEntityTags;
  instanceCounts: IInstanceCounts;
  instanceType?: string;
  instances: IInstance[];
  isDisabled?: boolean;
  launchConfig?: any;
  loadBalancers?: string[];
  name: string;
  provider?: string;
  region: string;
  runningExecutions?: IExecution[];
  runningTasks?: ITask[];
  searchField?: string;
  securityGroups?: string[];
  stack?: string;
  stringVal?: string;
  subnetType?: string;
  tags?: any;
  type: string;
  vpcName?: string;
  vpcId?: string;
  providerMetadata?: any;
}
