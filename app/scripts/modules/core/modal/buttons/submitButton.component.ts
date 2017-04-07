import {IComponentOptions, module} from 'angular';

export class SubmitButtonComponent implements IComponentOptions {
  public bindings: any = {
    onClick: '&',
    isDisabled: '<',
    isNew: '<',
    submitting: '<',
    label: '<',
  };
  public templateUrl = require('./submitButton.component.html');
}

export const SUBMIT_BUTTON_COMPONENT = 'spinnaker.core.modal.buttons.submitButton.component';
module(SUBMIT_BUTTON_COMPONENT, [])
  .component('submitButton', new SubmitButtonComponent());
