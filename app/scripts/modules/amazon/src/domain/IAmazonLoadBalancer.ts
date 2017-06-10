import { ILoadBalancer, IInstance, IInstanceCounts, IServerGroup, ISubnet } from '@spinnaker/core';

export interface IAmazonLoadBalancer extends ILoadBalancer {
  availabilityZones?: string[];
  credentials?: string;
  detachedInstances?: IInstance[];
  elb?: any;
  isInternal?: boolean;
  regionZones: string[];
  subnets?: string[];
  subnetDetails?: ISubnet[];
  subnetType?: string;
}

export interface IClassicListener {
  internalProtocol: string;
  internalPort: number;
  externalProtocol: string;
  externalPort: number;
  sslCertificateType?: string;
}

export interface IAmazonClassicLoadBalancer extends IAmazonLoadBalancer {
  healthCheckPath: string;
  healthCheckPort: number;
  healthCheckProtocol: string;
  healthTimeout: number;
  healthInterval: number;
  healthyThreshold: number;
  listeners: IClassicListener[];
  unhealthyThreshold: number;

}

export interface IAmazonApplicationLoadBalancer extends IAmazonLoadBalancer {
  listeners: IALBListener[];
  targetGroups: ITargetGroup[];
  ipAddressType?: string; // returned from clouddriver
}

export interface IALBListenerActions {
  targetGroupName: string;
  type: string;
}

export interface IALBListenerCertificate {
  type: string;
  name: string;
}
export interface IALBListener {
  certificates: IALBListenerCertificate[];
  defaultActions: IALBListenerActions[];
  port: number;
  protocol: string;
  sslPolicy?: string;
}

export interface ITargetGroupAttributes {
  deregistrationDelay: number;
  stickinessEnabled: boolean;
  stickinessType: string;
  stickinessDuration: number;
}

export interface ITargetGroup {
  account?: string; // returned from clouddriver
  attributes?: ITargetGroupAttributes;
  cloudProvider?: string; // returned from clouddriver
  detachedInstances?: IInstance[];
  healthCheckProtocol: string;
  healthCheckPort: number;
  healthCheckPath: string;
  healthTimeout: number;
  healthInterval: number;
  healthyThreshold: number;
  unhealthyThreshold: number;
  instanceCounts?: IInstanceCounts;
  instances?: IInstance[];
  loadBalancerNames?: string[]; // returned from clouddriver
  name: string;
  port: number;
  protocol: string;
  provider?: string;
  region?: string; // returned from clouddriver
  serverGroups?: IServerGroup[];
  type?: string; // returned from clouddriver
  vpcId?: string;
  vpcName?: string;
}
