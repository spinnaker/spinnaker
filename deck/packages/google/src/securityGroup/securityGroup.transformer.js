'use strict';

export const GOOGLE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER = 'spinnaker.gce.securityGroup.transformer';
export const name = GOOGLE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER; // for backwards compatibility
export class GceSecurityGroupTransformer {
  constructor($q = { when: (value) => Promise.resolve(value) }) {
    this.$q = $q;
  }

  normalizeSecurityGroup(securityGroup) {
    return this.$q.when(securityGroup); // no-op
  }
}
