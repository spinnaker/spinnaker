import { InstanceCounts } from './instanceCounts';


export class LoadBalancer {

  constructor(
    public name?: string,
    public type?: string,
    public vpcId?: string,
    public region?: string,
    public account?: string,
    public serverGroups?: any[],
    public healthState?:string,
    public instanceCounts?: InstanceCounts,
  ) { }

}
