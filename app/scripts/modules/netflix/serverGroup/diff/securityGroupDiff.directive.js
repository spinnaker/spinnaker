'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.serverGroup.diff.securityGroupDiff.directive', [
    require('../../../core/modal/wizard/v2modalWizard.service.js'),
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
  }).controller('netflixSecurityGroupDiffCtrl', function (v2modalWizardService) {
    this.acknowledgeSecurityGroupDiff = () => {
      this.command.viewState.securityGroupDiffs = [];
      v2modalWizardService.markClean('security-groups');
    };
  });
