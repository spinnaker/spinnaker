import { module } from 'angular';

import { ORACLE_COMMON_OCID_TRUNCATEOCID_FILTER } from './truncateOcid.filter';

('use strict');

export const ORACLE_COMMON_OCID_OCID_COMPONENT = 'spinnaker.oracle.ocid.component';
export const name = ORACLE_COMMON_OCID_OCID_COMPONENT; // for backwards compatibility
module(ORACLE_COMMON_OCID_OCID_COMPONENT, [ORACLE_COMMON_OCID_TRUNCATEOCID_FILTER]).component('ocid', {
  templateUrl: require('./ocid.template.html'),
  bindings: {
    ocid: '=',
    showOcid: '@',
  },
});
