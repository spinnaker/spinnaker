'use strict';

let angular = require('angular');

require('./modals.less');

module.exports = angular
  .module('spinnaker.core.modal', [
    require('./modalOverlay.directive.js'),
    require('./modalPage.directive.js'),
    require('./buttons/modalClose.directive.js'),
    require('./buttons/submitButton.directive.js'),
    require('./wizard/modalWizard.directive.js'),
    require('./wizard/modalWizard.service.js'),
    require('./wizard/wizardPage.directive.js'),
  ]).run(function($rootScope, $uibModalStack) {
    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      if (!fromParams.allowModalToStayOpen) {
        $uibModalStack.dismissAll();
      }
    });
  });
