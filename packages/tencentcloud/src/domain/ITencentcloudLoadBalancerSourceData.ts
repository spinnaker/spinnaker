import { ILoadBalancerSourceData } from '@spinnaker/core';

import { IListenerAction, NLBListenerProtocol } from './ITencentcloudLoadBalancer';

import { IListenerRule } from './ITencentcloudLoadBalancer';

export interface ITencentcloudContainerServerGroupSourceData {
  detachedInstances: string[];
  isDisabled: boolean;
  name: string;
  region: string;
}

export interface ITencentcloudLoadBalancerServerGroupSourceData extends ITencentcloudContainerServerGroupSourceData {
  instances: ITencentcloudLoadBalancerInstanceSourceData[];
}

export interface ITencentcloudTargetGroupServerGroupSourceData extends ITencentcloudContainerServerGroupSourceData {
  instances: ITencentcloudTargetGroupInstanceSourceData[];
}

export interface ITencentcloudInstanceHealthSourceData {
  type: string;
  state: 'InService' | 'OutOfService' | 'Unknown';
  reasonCode: 'ELB' | 'Instance' | 'N/A';
  description: string;
}

export interface ITencentcloudTargetHealthSourceData {
  description: string;
  reason: string;
  state: 'initial' | 'healthy' | 'unhealthy' | 'unused' | 'draining';
}

export interface IClassicListenerSourceData {
  instancePort: number;
  instanceProtocol: string;
  loadBalancerPort: number;
  protocol: string;
}

export interface ITencentcloudLoadBalancerSourceData extends ILoadBalancerSourceData {
  id?: string;
  account: string;
  availabilityZones: string[];
  cloudProvider: string;
  createdTime: number;
  dnsname: string;
  loadBalancerName: string;
  loadBalancerType?: string;
  name: string;
  region: string;
  scheme: 'internal' | 'internet-facing';
  securityGroups: string[];
  serverGroups: ITencentcloudLoadBalancerServerGroupSourceData[];
  subnets: string[];
  type: string;
  vpcId: string;
  // Some of the backend in clouddriver returns a vpcid (lowecase) only,
  // and was cached with some of that. Until caches roll off and we are
  // sure clouddriver is cleaed up, leave this dirtiness in here
  vpcid?: string;
}

export interface ITencentcloudLoadBalancerInstanceSourceData {
  id: string;
  zone: string;
  health: ITencentcloudInstanceHealthSourceData;
}

export interface ITencentcloudTargetGroupInstanceSourceData {
  id: string;
  zone: string;
  health: ITencentcloudTargetHealthSourceData;
}

export interface ITencentcloudTargetGroupSourceData {
  account: string;
  attributes: {
    'deregistration_delay.timeout_seconds': number;
    'stickiness.enabled': boolean;
    'stickiness.lb_cookie.duration_seconds': number;
    'stickiness.type': 'lb_cookie';
    'lambda.multi_value_headers.enabled': boolean;
  };
  cloudProvider: string;
  healthCheckIntervalSeconds: number;
  healthCheckPath: string;
  healthCheckPort: string;
  healthCheckProtocol: string;
  healthCheckTimeoutSeconds: number;
  healthyThresholdCount: number;
  instances: string[];
  loadBalancerNames: string[];
  matcher: {
    httpCode: string;
  };
  name: string;
  port: number;
  protocol: string;
  region: string;
  serverGroups: ITencentcloudTargetGroupServerGroupSourceData[];
  targetGroupArn: string;
  targetGroupName: string;
  targetType: string;
  type: string;
  unhealthyThresholdCount: number;
  vpcId: string;
}

export interface IApplicationLoadBalancerCertificateSourceData {
  certificateArn: string;
}

export interface IApplicationLoadBalancerListenerSourceData {
  certificates?: IApplicationLoadBalancerCertificateSourceData[];
  defaultActions: IListenerAction[];
  listenerArn: string;
  loadBalancerName: string;
  port: number;
  protocol: 'HTTP' | 'HTTPS';
  rules: IListenerRule[];
  sslPolicy?: string;
}

export interface IApplicationLoadBalancerSourceData extends ITencentcloudLoadBalancerSourceData {
  ipAddressType: 'ipv4' | 'dualstack';
  listeners: IApplicationLoadBalancerListenerSourceData[];
  loadBalancerArn: string;
  loadBalancerType: 'application';
  state: {
    code: 'active' | 'provisioning' | 'failed';
    reason?: string;
  };
  targetGroups: ITencentcloudTargetGroupSourceData[];
}

export interface INetworkLoadBalancerListenerSourceData {
  certificates?: IApplicationLoadBalancerCertificateSourceData[];
  defaultActions: Array<{
    targetGroupName: string;
    type: 'forward';
  }>;
  listenerArn: string;
  loadBalancerName: string;
  port: number;
  protocol: NLBListenerProtocol;
  rules: IListenerRule[];
  sslPolicy?: string;
}

export interface INetworkLoadBalancerSourceData extends ITencentcloudLoadBalancerSourceData {
  ipAddressType: 'ipv4' | 'dualstack';
  listeners: INetworkLoadBalancerListenerSourceData[];
  loadBalancerArn: string;
  loadBalancerType: 'network';
  state: {
    code: 'active' | 'provisioning' | 'failed';
    reason?: string;
  };
  targetGroups: ITencentcloudTargetGroupSourceData[];
}

export interface IClassicLoadBalancerSourceData extends ITencentcloudLoadBalancerSourceData {
  healthCheck: {
    healthyThreshold: number;
    interval: number;
    target: string;
    timeout: number;
    unhealthyThreshold: number;
  };
  instances: string[];
  listenerDescriptions: Array<{ listener: IClassicListenerSourceData; policyNames: string[] }>;
  policies: {
    appCookieStickinessPolicies: Array<{
      CookieName: string;
      PolicyName: string;
    }>;
    lbcookieStickinessPolicies: Array<{
      CookieExpirationPeriod: string;
      PolicyName: string;
    }>;
    otherPolicies: any[];
  };
  sourceSecurityGroup: {
    groupName: string;
    ownerAlias: string;
  };
}
