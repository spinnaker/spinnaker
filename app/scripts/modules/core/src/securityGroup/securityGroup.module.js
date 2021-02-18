'use strict';

import { module } from 'angular';

import { SECURITY_GROUP_FILTER } from './filter/securityGroup.filter.component';
import { SECURITY_GROUP_DATA_SOURCE } from './securityGroup.dataSource';
import { SECURITY_GROUP_STATES } from './securityGroup.states';
import './securityGroupSearchResultType';

export const CORE_SECURITYGROUP_SECURITYGROUP_MODULE = 'spinnaker.core.securityGroup';
export const name = CORE_SECURITYGROUP_SECURITYGROUP_MODULE; // for backwards compatibility
module(CORE_SECURITYGROUP_SECURITYGROUP_MODULE, [
  SECURITY_GROUP_FILTER,
  SECURITY_GROUP_DATA_SOURCE,
  SECURITY_GROUP_STATES,
]);
