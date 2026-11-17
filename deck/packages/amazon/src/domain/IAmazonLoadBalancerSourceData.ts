import type { ILoadBalancerSourceData } from '@spinnaker/core';

import type { IListenerAction, NLBListenerProtocol } from './IAmazonLoadBalancer';

import type { IListenerRule } from './IAmazonLoadBalancer';

export interface IAmazonContainerServerGroupSourceData {
  detachedInstances: string[];
  isDisabled: boolean;
  name: string;
  region: string;
}

export interface IAmazonLoadBalancerServerGroupSourceData extends IAmazonContainerServerGroupSourceData {
  instances: IAmazonLoadBalancerInstanceSourceData[];
}

export interface IAmazonTargetGroupServerGroupSourceData extends IAmazonContainerServerGroupSourceData {
  instances: IAmazonTargetGroupInstanceSourceData[];
}

export interface IAmazonInstanceHealthSourceData {
  type: string;
  state: 'InService' | 'OutOfService' | 'Unknown';
  reasonCode: 'ELB' | 'Instance' | 'N/A';
  description: string;
}

export interface IAmazonTargetHealthSourceData {
  description: string;
  reason: string;
  state: 'initial' | 'healthy' | 'unhealthy' | 'unused' | 'draining';
}

export interface IClassicListenerSourceData {
  instancePort: number;
  instanceProtocol: string;
  loadBalancerPort: number;
  protocol: string;
  sslCertificateId?: string;
  sslcertificateId?: string; // legacy: pre-AWS SDK v2 builder name; remove once caches have refreshed
}

export interface IAmazonLoadBalancerSourceData extends ILoadBalancerSourceData {
  account: string;
  availabilityZones: string[];
  cloudProvider: string;
  createdTime: number;
  dnsName?: string;
  dnsname?: string; // legacy: pre-AWS SDK v2 builder name; remove once caches have refreshed
  loadBalancerName: string;
  loadBalancerType?: string;
  name: string;
  region: string;
  scheme: 'internal' | 'internet-facing';
  securityGroups: string[];
  serverGroups: IAmazonLoadBalancerServerGroupSourceData[];
  subnets: string[];
  type: string;
  vpcId: string;
  vpcid?: string; // legacy: pre-AWS SDK v2 builder name; remove once caches have refreshed
}

export interface IAmazonLoadBalancerInstanceSourceData {
  id: string;
  zone: string;
  health: IAmazonInstanceHealthSourceData;
}

export interface IAmazonTargetGroupInstanceSourceData {
  id: string;
  zone: string;
  health: IAmazonTargetHealthSourceData;
}

export interface IAmazonTargetGroupSourceData {
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
  serverGroups: IAmazonTargetGroupServerGroupSourceData[];
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

export interface IApplicationLoadBalancerSourceData extends IAmazonLoadBalancerSourceData {
  ipAddressType: 'ipv4' | 'dualstack';
  listeners: IApplicationLoadBalancerListenerSourceData[];
  loadBalancerArn: string;
  loadBalancerType: 'application';
  state: {
    code: 'active' | 'provisioning' | 'failed';
    reason?: string;
  };
  targetGroups: IAmazonTargetGroupSourceData[];
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

export interface INetworkLoadBalancerSourceData extends IAmazonLoadBalancerSourceData {
  ipAddressType: 'ipv4' | 'dualstack';
  listeners: INetworkLoadBalancerListenerSourceData[];
  loadBalancerArn: string;
  loadBalancerType: 'network';
  state: {
    code: 'active' | 'provisioning' | 'failed';
    reason?: string;
  };
  targetGroups: IAmazonTargetGroupSourceData[];
}

export interface IClassicLoadBalancerSourceData extends IAmazonLoadBalancerSourceData {
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
    lbCookieStickinessPolicies: Array<{
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
