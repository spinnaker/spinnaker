import { module } from 'angular';
import { react2angular } from 'react2angular';

import { SubnetTag } from './SubnetTag';
import { withErrorBoundary } from '../presentation/SpinErrorBoundary';

('use strict');

export const CORE_SUBNET_SUBNETTAG_COMPONENT = 'spinnaker.core.subnet.tag.component';
export const name = CORE_SUBNET_SUBNETTAG_COMPONENT; // for backwards compatibility
module(CORE_SUBNET_SUBNETTAG_COMPONENT, []).component(
  'subnetTag',
  react2angular(withErrorBoundary(SubnetTag, 'subnetTag'), ['subnetId']),
);
