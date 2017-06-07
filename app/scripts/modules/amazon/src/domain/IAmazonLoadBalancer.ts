import { ILoadBalancer, IInstance, IInstanceCounts, IServerGroup, ISubnet } from '@spinnaker/core';

export interface IAmazonLoadBalancer extends ILoadBalancer {
  elb: any;
  subnets: string[];
  subnetDetails: ISubnet[];
}

export interface IAmazonClassicLoadBalancer extends IAmazonLoadBalancer {

}

export interface IAmazonApplicationLoadBalancer extends IAmazonLoadBalancer {
  listeners: IALBListener[];
  targetGroups: ITargetGroup[];
  ipAddressType: string;
}

export interface IALBListenerActions {
  targetGroupName: string;
  type: string;
}

export interface IALBListener {
  certificates: string[];
  defaultActions: IALBListenerActions[];
  loadBalancerName: string;
  name: string;
  port: number;
  protocol: string;
  sslPolicy: string;
}

export interface ITargetGroup {
  account: string;
  cloudProvider: string;
  instanceCounts?: IInstanceCounts;
  instances?: IInstance[];
  loadBalancerNames: string[];
  name: string;
  region: string;
  serverGroups?: IServerGroup[];
  type: string;
  vpcId: string;
}
