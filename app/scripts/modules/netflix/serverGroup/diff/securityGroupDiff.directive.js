'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.serverGroup.diff.securityGroupDiff.directive', [
    require('../../../core/modal/wizard/modalWizard.service.js'),
  ])
  .directive('netflixSecurityGroupDiff', function () {
    return {
      restrict: 'E',
      templateUrl: require('./securityGroupDiff.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: 'netflixSecurityGroupDiffCtrl',
    };
  }).controller('netflixSecurityGroupDiffCtrl', function (modalWizardService) {
    this.acknowledgeSecurityGroupDiff = () => {
      this.command.viewState.securityGroupDiffs = [];
      modalWizardService.getWizard().markClean('security-groups');
    };
  });
