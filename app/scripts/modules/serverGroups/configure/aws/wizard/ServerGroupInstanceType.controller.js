'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws')
  .controller('awsInstanceTypeCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markComplete('instance-type');
    modalWizardService.getWizard().markClean('instance-type');

  }).name;
