import type { IComponentOptions } from 'angular';
import { module } from 'angular';

export const submitButtonComponent: IComponentOptions = {
  bindings: {
    onClick: '&',
    isDisabled: '<',
    isNew: '<',
    submitting: '<',
    label: '<',
  },
  template: `
    <button class="btn btn-primary" ng-disabled="$ctrl.isDisabled" ng-click="$ctrl.onClick()">
      <div class="flex-container-h horizontal middle">
        <i ng-if="!$ctrl.submitting" class="far fa-check-circle"></i>
        <loading-spinner ng-if="$ctrl.submitting" mode="'circular'"></loading-spinner>
        <span class="sp-margin-xs-left">{{$ctrl.label || ($ctrl.isNew ? 'Create' : 'Update')}}</span>
      </div>
    </button>`,
};

export const SUBMIT_BUTTON_COMPONENT = 'spinnaker.core.modal.buttons.submitButton.component';
module(SUBMIT_BUTTON_COMPONENT, []).component('submitButton', submitButtonComponent);
