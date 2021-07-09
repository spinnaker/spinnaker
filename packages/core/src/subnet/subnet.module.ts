import { module } from 'angular';
import { CORE_SUBNET_SUBNETTAG_COMPONENT } from './subnetTag.component';

export const SUBNET_MODULE = 'spinnaker.core.subnet.module';
module(SUBNET_MODULE, [CORE_SUBNET_SUBNETTAG_COMPONENT]);
