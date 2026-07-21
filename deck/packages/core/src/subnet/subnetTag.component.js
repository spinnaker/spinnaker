import { module } from 'angular';

import { SubnetTag } from './SubnetTag';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

('use strict');

export const CORE_SUBNET_SUBNETTAG_COMPONENT = 'spinnaker.core.subnet.tag.component';
export const name = CORE_SUBNET_SUBNETTAG_COMPONENT; // for backwards compatibility
module(CORE_SUBNET_SUBNETTAG_COMPONENT, []).component(
  'subnetTag',
  angularComponentFromReact(SubnetTag, 'subnetTag', ['subnetId']),
);
