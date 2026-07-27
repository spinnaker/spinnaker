'use strict';

export class GceSecurityGroupTransformer {
  constructor(promiseService) {
    this.promiseService = promiseService;
  }

  normalizeSecurityGroup(securityGroup) {
    return this.promiseService.resolve(securityGroup);
  }
}
