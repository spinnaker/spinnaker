import {
  IInstance,
  IInstanceCounts,
  ILoadBalancer,
  ILoadBalancerDeleteCommand,
  ILoadBalancerUpsertCommand,
  ISubnet,
} from '@spinnaker/core';

import { IAmazonLoadBalancerSourceData } from './IAmazonLoadBalancerSourceData';
import { IAmazonServerGroup } from './IAmazonServerGroup';
import { IAuthenticateOidcActionConfig } from '../loadBalancer/OidcConfigReader';

export type ClassicListenerProtocol = 'HTTP' | 'HTTPS' | 'TCP' | 'SSL';
export type ALBListenerProtocol = 'HTTP' | 'HTTPS';
export type IListenerActionType = 'forward' | 'authenticate-oidc' | 'redirect';
export type NLBListenerProtocol = 'TCP' | 'TLS' | 'UDP';

export interface IAmazonLoadBalancer extends ILoadBalancer {
  availabilityZones?: string[];
  credentials?: string;
  detachedInstances?: IInstance[];
  elb?: IAmazonLoadBalancerSourceData;
  isInternal?: boolean;
  regionZones: string[];
  serverGroups: IAmazonServerGroup[];
  subnets?: string[];
  subnetDetails?: ISubnet[];
  subnetType?: string;
}

export interface IClassicListener {
  internalProtocol: ClassicListenerProtocol;
  internalPort: number;
  externalProtocol: ClassicListenerProtocol;
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
  idleTimeout?: number;
}

export interface IAmazonApplicationLoadBalancer extends IAmazonLoadBalancer {
  listeners: IALBListener[];
  targetGroups: ITargetGroup[];
  ipAddressType?: string; // returned from clouddriver
  deletionProtection: boolean;
  idleTimeout: number;
  loadBalancingCrossZone: boolean;
}

export interface IAmazonNetworkLoadBalancer extends IAmazonLoadBalancer {
  listeners: INLBListener[];
  targetGroups: ITargetGroup[];
  ipAddressType?: string; // returned from clouddriver
  deletionProtection: boolean;
  idleTimeout: number;
  loadBalancingCrossZone: boolean;
}

export interface IRedirectActionConfig {
  host?: string;
  path?: string;
  port?: string;
  protocol?: 'HTTP' | 'HTTPS' | '#{protocol}';
  query?: string;
  statusCode: 'HTTP_301' | 'HTTP_302';
}

export interface IListenerAction {
  authenticateOidcConfig?: IAuthenticateOidcActionConfig;
  order?: number;
  redirectActionConfig?: IRedirectActionConfig; // writes
  redirectConfig?: IRedirectActionConfig; // reads
  targetGroupName?: string;
  type: IListenerActionType;
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
  rules: IListenerRule[];
  sslPolicy?: string;
}

export interface INLBListener {
  certificates: IALBListenerCertificate[];
  defaultActions: IListenerAction[];
  port: number;
  protocol: string;
  rules: IListenerRule[];
  sslPolicy?: string;
}

export interface IListenerRule {
  actions: IListenerAction[];
  default?: boolean;
  conditions: IListenerRuleCondition[];
  priority: number | 'default';
}

export type ListenerRuleConditionField = 'path-pattern' | 'host-header' | 'http-request-method';

export interface IListenerRuleCondition {
  field: ListenerRuleConditionField;
  httpRequestMethodConfig?: {
    values: string[];
  };
  values: string[];
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
  healthCheckPort: number | 'traffic-port';
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
  serverGroups?: IAmazonServerGroup[];
  targetType?: string;
  type: string; // returned from clouddriver
  vpcId?: string;
  vpcName?: string;
}

export interface IListenerDescription {
  certificates?: IALBListenerCertificate[];
  protocol: ALBListenerProtocol | NLBListenerProtocol;
  port: number;
  sslPolicy?: string;
  defaultActions: IListenerAction[];
  rules?: IListenerRule[];
}

export interface IALBTargetGroupDescription {
  name: string;
  protocol: 'HTTP' | 'HTTPS' | 'TCP' | 'TLS';
  port: number;
  targetType: 'instance' | 'ip' | 'lambda';
  attributes: {
    // Defaults to 300
    deregistrationDelay?: number;
    // Defaults to false
    stickinessEnabled?: boolean;
    // Defaults to 'lb_cookie'. The only option for now, but they promise there will be more...
    stickinessType?: 'lb_cookie';
    // Defaults to 86400
    stickinessDuration?: number;
    multiValueHeadersEnabled?: boolean;
  };
  // Defaults to 10
  healthCheckInterval?: number;
  // Defaults to '200-299'
  healthCheckMatcher?: string;
  healthCheckPath: string;
  healthCheckPort: string;
  healthCheckProtocol: 'HTTP' | 'HTTPS' | 'TCP';
  // Defaults to 10
  healthyThreshold?: number;
  // Defaults to 5
  healthCheckTimeout?: number;
  // Defaults to 2
  unhealthyThreshold?: number;
}

export interface INLBTargetGroupDescription {
  name: string;
  protocol: 'TCP';
  port: number;
  targetType: 'instance' | 'ip';
  attributes: {
    // Defaults to 300
    deregistrationDelay?: number;
    deregistrationDelayConnectionTermination?: boolean;
    preserveClientIp?: boolean;
  };
  // Defaults to 10
  healthCheckInterval?: number;
  healthCheckPort: string;
  healthCheckProtocol: 'TCP' | 'HTTP' | 'HTTPS';
  healthCheckPath: string;
  // Defaults to 10
  healthyThreshold?: number;
  // Defaults to 5
  healthCheckTimeout?: number;
  // Defaults to 10
  unhealthyThreshold?: number;
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
  usePreferredZones?: boolean;
  vpcId: string;
}

export interface IAmazonLoadBalancerDeleteCommand extends ILoadBalancerDeleteCommand {
  loadBalancerType: string;
}

export interface IClassicListenerDescription extends IClassicListener {
  sslCertificateId?: string;
  sslCertificateName?: string;
  policyNames?: string[];
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
  idleTimeout?: number;
}

export interface IAmazonApplicationLoadBalancerUpsertCommand extends IAmazonLoadBalancerUpsertCommand {
  deletionProtection: boolean;
  ipAddressType?: string;
  idleTimeout: number;
  listeners: IListenerDescription[];
  targetGroups: IALBTargetGroupDescription[];
}

export interface IAmazonNetworkLoadBalancerUpsertCommand extends IAmazonLoadBalancerUpsertCommand {
  deletionProtection: boolean;
  loadBalancingCrossZone: boolean;
  listeners: IListenerDescription[];
  targetGroups: INLBTargetGroupDescription[];
}
