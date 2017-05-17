import { module } from 'angular';

export const SUBNET_MODULE = 'spinnaker.core.subnet.module';
module(SUBNET_MODULE, [
  require('./subnetTag.component'),
]);
