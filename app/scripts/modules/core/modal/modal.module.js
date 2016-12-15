'use strict';

import {V2_MODAL_WIZARD_COMPONENT} from './wizard/v2modalWizard.component';
import {V2_MODAL_WIZARD_SERVICE} from './wizard/v2modalWizard.service';
import {MODAL_CLOSE_COMPONENT} from './buttons/modalClose.component';

let angular = require('angular');

require('./modals.less');

module.exports = angular
  .module('spinnaker.core.modal', [
    require('./modalOverlay.directive.js'),
    require('./modalPage.directive.js'),
    MODAL_CLOSE_COMPONENT,
    require('./buttons/submitButton.directive.js'),
    require('./wizard/modalWizard.directive.js'),
    V2_MODAL_WIZARD_SERVICE,
    require('./wizard/wizardPage.directive.js'),
    V2_MODAL_WIZARD_COMPONENT,
  ]).run(function($rootScope, $uibModalStack) {
    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      if (!fromParams.allowModalToStayOpen) {
        $uibModalStack.dismissAll();
      }
    });
  });
