import { module } from 'angular';
import { AMAZON_VPC_VPCTAG_DIRECTIVE } from './vpcTag.directive';

export const VPC_MODULE = 'spinnaker.amazon.vpc';
module(VPC_MODULE, [AMAZON_VPC_VPCTAG_DIRECTIVE]);
