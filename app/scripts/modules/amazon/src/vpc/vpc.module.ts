import { module } from 'angular';

export const VPC_MODULE = 'spinnaker.amazon.vpc';
module(VPC_MODULE, [
  require('./vpcTag.directive')
]);
