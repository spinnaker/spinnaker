'use strict';

import { SubnetTag } from './SubnetTag';
import { react2angular } from 'react2angular';

const angular = require('angular');

export const CORE_SUBNET_SUBNETTAG_COMPONENT = 'spinnaker.core.subnet.tag.component';
export const name = CORE_SUBNET_SUBNETTAG_COMPONENT; // for backwards compatibility
angular.module(CORE_SUBNET_SUBNETTAG_COMPONENT, []).component('subnetTag', react2angular(SubnetTag, ['subnetId']));
