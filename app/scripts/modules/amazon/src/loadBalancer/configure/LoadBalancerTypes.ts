import { ILoadBalancerModalProps } from '@spinnaker/core';

import { IAmazonLoadBalancerUpsertCommand } from 'amazon/domain';

import { CreateApplicationLoadBalancer } from './application/CreateApplicationLoadBalancer';
import { CreateClassicLoadBalancer } from './classic/CreateClassicLoadBalancer';
import { CreateNetworkLoadBalancer } from './network/CreateNetworkLoadBalancer';

export interface IAmazonLoadBalancerConfig {
  type: string;
  label: string;
  sublabel: string;
  description: string;
  component: React.ComponentClass<ILoadBalancerModalProps> & {
    show: (props: ILoadBalancerModalProps) => Promise<IAmazonLoadBalancerUpsertCommand>;
  };
}

export const LoadBalancerTypes: IAmazonLoadBalancerConfig[] = [
  {
    type: 'application',
    label: 'Application',
    sublabel: 'ALB',
    description: 'Highly configurable, application-focused balancer. HTTP and HTTPS only.',
    component: CreateApplicationLoadBalancer,
  },
  {
    type: 'network',
    label: 'Network',
    sublabel: 'NLB',
    description: 'Basic, high-performance balancer with fixed IP.',
    component: CreateNetworkLoadBalancer,
  },
  {
    type: 'classic',
    label: 'Classic',
    sublabel: 'Legacy',
    description: 'Previous generation balancer (ELB).',
    component: CreateClassicLoadBalancer,
  },
];
