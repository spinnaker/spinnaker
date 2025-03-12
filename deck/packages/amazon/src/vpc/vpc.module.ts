import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { VpcTag } from './VpcTag';

export const VPC_MODULE = 'spinnaker.amazon.vpc';
module(VPC_MODULE, []).component('vpcTag', react2angular(withErrorBoundary(VpcTag, 'vpcTag'), ['vpcId']));
