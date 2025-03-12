'use strict';

import { module } from 'angular';

import { MODAL_CLOSE_COMPONENT } from './buttons/modalClose.component';
import { SUBMIT_BUTTON_COMPONENT } from './buttons/submitButton.component';
import { CORE_MODAL_MODALOVERLAY_DIRECTIVE } from './modalOverlay.directive';
import { CORE_MODAL_MODALPAGE_DIRECTIVE } from './modalPage.directive';
import { V2_MODAL_WIZARD_COMPONENT } from './wizard/v2modalWizard.component';
import { CORE_MODAL_WIZARD_WIZARDSUBFORMVALIDATION_SERVICE } from './wizard/wizardSubFormValidation.service';

import './modals.less';

export const CORE_MODAL_MODAL_MODULE = 'spinnaker.core.modal';
export const name = CORE_MODAL_MODAL_MODULE; // for backwards compatibility
module(CORE_MODAL_MODAL_MODULE, [
  CORE_MODAL_MODALOVERLAY_DIRECTIVE,
  CORE_MODAL_MODALPAGE_DIRECTIVE,
  CORE_MODAL_WIZARD_WIZARDSUBFORMVALIDATION_SERVICE,
  MODAL_CLOSE_COMPONENT,
  SUBMIT_BUTTON_COMPONENT,
  V2_MODAL_WIZARD_COMPONENT,
]).run([
  '$rootScope',
  '$uibModalStack',
  function ($rootScope, $uibModalStack) {
    $rootScope.$on('$stateChangeStart', function (event, toState, toParams) {
      if (!toParams.allowModalToStayOpen) {
        $uibModalStack.dismissAll();
      }
    });
  },
]);
