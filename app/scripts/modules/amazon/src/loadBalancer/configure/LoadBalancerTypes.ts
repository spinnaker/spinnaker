import { Application } from '@spinnaker/core';

import { IAmazonLoadBalancer, IAmazonLoadBalancerUpsertCommand } from 'amazon';

import { CreateApplicationLoadBalancer } from './application/CreateApplicationLoadBalancer';
import { CreateClassicLoadBalancer } from './classic/CreateClassicLoadBalancer';

export interface IAmazonLoadBalancerModalProps {
  app: Application;
  forPipelineConfig?: boolean;
  loadBalancer: IAmazonLoadBalancer;
  show: boolean;
  showCallback: (show: boolean) => void;
  onComplete?: (loadBalancerCommand: IAmazonLoadBalancerUpsertCommand) => void;
}

export interface IAmazonLoadBalancerConfig {
  type: string;
  label: string;
  sublabel: string;
  description: string;
  component: React.ComponentClass<IAmazonLoadBalancerModalProps>;
}

export const LoadBalancerTypes: IAmazonLoadBalancerConfig[] = [
  {
    type: 'application',
    label: 'Application',
    sublabel: 'ALB',
    description: 'Highly configurable, application-focused balancer. HTTP and HTTPS only.',
    component: CreateApplicationLoadBalancer,
  },
  // {
  //   type: 'network',
  //   label: 'Network',
  //   sublabel: 'NLB',
  //   description: 'Basic, high-performance balancer with fixed IP.',
  //   component: CreateNetworkLoadBalancer,
  // },
  {
    type: 'classic',
    label: 'Classic',
    sublabel: 'Legacy',
    description: 'Previous generation balancer (ELB).',
    component: CreateClassicLoadBalancer,
  },
];
