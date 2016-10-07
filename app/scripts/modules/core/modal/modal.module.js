'use strict';

import v2modalWizardModule from './wizard/v2modalWizard.component';
import modalWizardServiceModule from './wizard/v2modalWizard.service';
import modalCloseModule from './buttons/modalClose.component';

let angular = require('angular');

require('./modals.less');

module.exports = angular
  .module('spinnaker.core.modal', [
    require('./modalOverlay.directive.js'),
    require('./modalPage.directive.js'),
    modalCloseModule,
    require('./buttons/submitButton.directive.js'),
    require('./wizard/modalWizard.directive.js'),
    modalWizardServiceModule,
    require('./wizard/wizardPage.directive.js'),
    v2modalWizardModule,
  ]).run(function($rootScope, $uibModalStack) {
    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      if (!fromParams.allowModalToStayOpen) {
        $uibModalStack.dismissAll();
      }
    });
  });
