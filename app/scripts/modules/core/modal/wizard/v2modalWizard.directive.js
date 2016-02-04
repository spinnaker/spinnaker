'use strict';

let angular = require('angular');

require('./modalWizard.less');

module.exports = angular.module('spinnaker.core.modalWizard.wizard.v2', [
  require('./v2modalWizard.service.js'),
  require('./v2wizardPage.directive.js'),
])
  .directive('v2ModalWizard', function (v2modalWizardService) {
    return {
      restrict: 'E',
      transclude: true,
      scope: true,
      templateUrl: require('./v2modalWizard.directive.html'),
      controller: 'v2ModalWizardCtrl as wizardCtrl',
      link: function(scope, elem, attrs) {
        v2modalWizardService.setHeading(attrs.heading);
      }
    };
  }
).controller('v2ModalWizardCtrl', function($scope, v2modalWizardService) {
    $scope.wizard = v2modalWizardService;
    $scope.$on('waypoints-changed', (event, snapshot) => {
      let ids = snapshot.lastWindow.map((entry) => entry.elem);
      ids.reverse().forEach((id) => v2modalWizardService.setCurrentPage($scope.wizard.getPage(id), true));
    });

    $scope.$on('$destroy', v2modalWizardService.resetWizard);
  }
);
