import { IAmazonLoadBalancerSourceData } from './IAmazonLoadBalancerSourceData';
import { ILoadBalancer, ILoadBalancerUpsertCommand, IInstance, IInstanceCounts, IServerGroup, ISubnet } from '@spinnaker/core';

export interface IAmazonLoadBalancer extends ILoadBalancer {
  availabilityZones?: string[];
  credentials?: string;
  detachedInstances?: IInstance[];
  elb?: IAmazonLoadBalancerSourceData;
  isInternal?: boolean;
  regionZones: string[];
  subnets?: string[];
  subnetDetails?: ISubnet[];
  subnetType?: string;
}

export interface IClassicListener {
  internalProtocol: 'HTTP' | 'HTTPS' | 'TCP' | 'SSL';
  internalPort: number;
  externalProtocol: 'HTTP' | 'HTTPS' | 'TCP' | 'SSL';
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

export interface IListenerAction {
  targetGroupName: string;
  type: string;
}

export interface IALBListenerCertificate {
  certificateArn: string;
  type: string;
  name: string;
}
export interface IALBListener {
  certificates: IALBListenerCertificate[];
  defaultActions: IListenerAction[];
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
  account: string; // returned from clouddriver
  attributes?: ITargetGroupAttributes;
  cloudProvider: string; // returned from clouddriver
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
  loadBalancerNames: string[]; // returned from clouddriver
  name: string;
  port: number;
  protocol: string;
  provider?: string;
  region: string; // returned from clouddriver
  serverGroups?: IServerGroup[];
  type: string; // returned from clouddriver
  vpcId?: string;
  vpcName?: string;
}

export interface IAmazonLoadBalancerUpsertCommand extends ILoadBalancerUpsertCommand {
  availabilityZones: { [region: string]: string[] };
  isInternal: boolean;
  // listeners will be overriden and re-typed by extending types (application, classic)
  listeners: any[];
  // If loadBalancerType is not provided, will default to 'classic' for bwc
  loadBalancerType?: 'classic' | 'application' | 'network';
  regionZones: string[];
  securityGroups: string[];
  subnetType: string;
  vpcId: string;
}

export interface IClassicListenerDescription extends IClassicListener {
  sslCertificateId?: string;
  sslCertificateName?: string;
}

export interface IAmazonClassicLoadBalancerUpsertCommand extends IAmazonLoadBalancerUpsertCommand {
  healthCheck: string;
  healthCheckPath: string;
  healthCheckProtocol: string;
  healthCheckPort: number;
  healthInterval?: number;
  healthTimeout?: number;
  healthyThreshold?: number;
  listeners: IClassicListenerDescription[];
  unhealthyThreshold?: number;
}

export interface IAmazonApplicationLoadBalancerUpsertCommand extends IAmazonLoadBalancerUpsertCommand {
  listeners: {
    certificates?: {
      certificateArn: string;
      name?: string; // Only used while creating the description
      type?: string; // Only used while creating the description
    }[];
    protocol: 'HTTP' | 'HTTPS';
    port: number;
    sslPolicy?: string;
    defaultActions: IListenerAction[];
    rules?: {
      actions: IListenerAction[];
      priority: number;
      ruleConditions: {
        field: 'path-pattern' | 'host-header';
        values: string[];
      }[];
    }[];
  }[];
  targetGroups: {
    name: string;
    protocol: 'HTTP' | 'HTTPS';
    port: number;
    attributes: {
      // Defaults to 300
      deregistrationDelay?: number;
      // Defaults to false
      stickinessEnabled?: boolean;
      // Defaults to 'lb_cookie'. The only option for now, but they promise there will be more...
      stickinessType?: 'lb_cookie';
      // Defaults to 86400
      stickinessDuration?: number;
    };
    // Defaults to 10
    healthCheckInterval?: number;
    // Defaults to '200-299'
    healthCheckMatcher?: string;
    healthCheckPath: string;
    healthCheckPort: string;
    healthCheckProtocol: 'HTTP' | 'HTTPS';
    // Defaults to 10
    healthyThreshold?: number;
    // Defaults to 5
    healthCheckTimeout?: number;
    // Defaults to 2
    unhealthyThreshold?: number;
  }[];
}
