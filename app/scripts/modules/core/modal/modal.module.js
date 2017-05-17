'use strict';

const angular = require('angular');

import {V2_MODAL_WIZARD_COMPONENT} from './wizard/v2modalWizard.component';
import {V2_MODAL_WIZARD_SERVICE} from './wizard/v2modalWizard.service';
import {MODAL_CLOSE_COMPONENT} from './buttons/modalClose.component';
import {SUBMIT_BUTTON_COMPONENT} from './buttons/submitButton.component';

import './modals.less';

module.exports = angular
  .module('spinnaker.core.modal', [
    require('./modalOverlay.directive.js'),
    require('./modalPage.directive.js'),
    require('./wizard/wizardSubFormValidation.service'),
    require('./closeable/closeable.modal.controller'),
    MODAL_CLOSE_COMPONENT,
    SUBMIT_BUTTON_COMPONENT,
    V2_MODAL_WIZARD_SERVICE,
    V2_MODAL_WIZARD_COMPONENT,
  ]).run(function($rootScope, $uibModalStack) {
    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      if (!fromParams.allowModalToStayOpen) {
        $uibModalStack.dismissAll();
      }
    });
  });
