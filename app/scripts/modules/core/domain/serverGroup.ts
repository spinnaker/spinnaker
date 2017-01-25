import {IEntityTags} from './IEntityTags';
import {Instance} from './instance';
import {InstanceCounts} from './instanceCounts';
import {Execution} from './execution';
import {ITask} from '../task/task.read.service';

// remnant from legacy code
interface IAsg {
  minSize: number;
  maxSize: number;
  desiredCapacity: number;
}

export interface ServerGroup {
  account: string;
  app?: string;
  asg?: IAsg;
  buildInfo?: any;
  category?: string;
  cloudProvider: string;
  cluster: string;
  clusterEntityTags?: IEntityTags[];
  detail?: string;
  entityTags?: IEntityTags;
  instanceCounts: InstanceCounts;
  instanceType?: string;
  instances: Instance[];
  isDisabled?: boolean;
  launchConfig?: any;
  loadBalancers?: string[];
  name: string;
  provider?: string;
  region: string;
  runningExecutions?: Execution[];
  runningTasks?: ITask[];
  searchField?: string;
  securityGroups?: string[];
  stringVal?: string;
  tags?: any;
  type: string;
  vpcName?: string;
}
