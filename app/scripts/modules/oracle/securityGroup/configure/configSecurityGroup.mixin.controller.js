'use strict';

const angular = require('angular');

import {
  ACCOUNT_SERVICE,
  SECURITY_GROUP_WRITER,
  SECURITY_GROUP_READER,
  V2_MODAL_WIZARD_SERVICE,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.oraclebmcs.securityGroup.baseConfig.controller', [
    SECURITY_GROUP_WRITER,
    SECURITY_GROUP_READER,
    ACCOUNT_SERVICE,
    V2_MODAL_WIZARD_SERVICE,
    require('@uirouter/angularjs').default,
  ])
  .controller('oraclebmcsConfigSecurityGroupMixin', function ($scope,
                                                              $state,
                                                              $uibModalInstance,
                                                              taskMonitorService,
                                                              application,
                                                              securityGroup,
                                                              securityGroupReader,
                                                              securityGroupWriter,
                                                              accountService,
                                                              v2modalWizardService) {

    $scope.wizard = v2modalWizardService;

    this.initializeSecurityGroups = () => {
      return $scope.state.securityGroupsLoaded = true;
    };

    this.cancel = () => {
      $uibModalInstance.dismiss();
    };
  });
