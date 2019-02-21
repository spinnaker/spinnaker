'use strict';

const angular = require('angular');

import { V2_MODAL_WIZARD_COMPONENT } from './wizard/v2modalWizard.component';
import { MODAL_CLOSE_COMPONENT } from './buttons/modalClose.component';
import { SUBMIT_BUTTON_COMPONENT } from './buttons/submitButton.component';

import './modals.less';

module.exports = angular
  .module('spinnaker.core.modal', [
    require('./modalOverlay.directive').name,
    require('./modalPage.directive').name,
    require('./wizard/wizardSubFormValidation.service').name,
    MODAL_CLOSE_COMPONENT,
    SUBMIT_BUTTON_COMPONENT,
    V2_MODAL_WIZARD_COMPONENT,
  ])
  .run([
    '$rootScope',
    '$uibModalStack',
    function($rootScope, $uibModalStack) {
      $rootScope.$on('$stateChangeStart', function(event, toState, toParams) {
        if (!toParams.allowModalToStayOpen) {
          $uibModalStack.dismissAll();
        }
      });
    },
  ]);
