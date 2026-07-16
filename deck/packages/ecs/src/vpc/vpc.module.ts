import { module } from 'angular';
import { react2angular } from 'react2angular';

import { VpcTag } from '@spinnaker/amazon';
import { withErrorBoundary } from '@spinnaker/core';

export const ECS_VPC_MODULE = 'spinnaker.ecs.vpc';

module(ECS_VPC_MODULE, []).component('vpcTag', react2angular(withErrorBoundary(VpcTag, 'vpcTag'), ['vpcId']));
