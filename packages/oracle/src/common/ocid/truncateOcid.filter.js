'use strict';

import { module } from 'angular';

export const ORACLE_COMMON_OCID_TRUNCATEOCID_FILTER = 'spinnaker.oracle.truncateOcid.filter';
export const name = ORACLE_COMMON_OCID_TRUNCATEOCID_FILTER; // for backwards compatibility
module(ORACLE_COMMON_OCID_TRUNCATEOCID_FILTER, []).filter('truncateOcid', function () {
  return function (ocid) {
    if (ocid) {
      return '...' + ocid.substr(ocid.length - 6);
    }
    return '';
  };
});
