export interface IAwsLoadBalancerConfig {
  type: string;
  label: string;
  sublabel: string;
  description: string;
  createTemplateUrl: string;
  editTemplateUrl: string;
  controller: string;
}

export const LoadBalancerTypes: IAwsLoadBalancerConfig[] = [
  {
    type: 'application',
    label: 'Application',
    sublabel: 'ALB',
    description: 'Highly configurable, application-focused balancer.',
    createTemplateUrl: require('../application/createApplicationLoadBalancer.html'),
    editTemplateUrl: require('../application/editApplicationLoadBalancer.html'),
    controller: 'awsCreateApplicationLoadBalancerCtrl'
  },
  // {
  //   type: 'network',
  //   label: 'Network',
  //   sublabel: 'NLB',
  //   description: 'Basic, high-performance balancer with fixed IP.',
  //   createTemplateUrl: require('../network/networkCreateLoadBalancer.html'),
  //   editTemplateUrl: require('../network/networkEditLoadBalancer.html'),
  //   controller: 'awsNetworkLoadBalancerCtrl'
  // },
  {
    type: 'classic',
    label: 'Classic',
    sublabel: 'Legacy',
    description: 'Previous generation balancer.',
    createTemplateUrl: require('../classic/createClassicLoadBalancer.html'),
    editTemplateUrl: require('../classic/editClassicLoadBalancer.html'),
    controller: 'awsCreateClassicLoadBalancerCtrl'
  },
];
