import {module} from 'angular';
import {
  APPLICATION_NAME_VALIDATOR,
  ApplicationNameValidator, IApplicationNameValidationResult
} from 'core/application/modal/validation/applicationName.validator';

/**
 * This directive is responsible for rendering error and warning messages to the screen when creating a new application.
 * It does NOT set the validity of the form field - that is handled by the validateApplicationName directive.
 */
class ApplicationNameValidationMessagesController implements ng.IComponentController {
  public name: string;
  public cloudProviders: string[];
  public messages: IApplicationNameValidationResult;

  static get $inject() { return ['applicationNameValidator']; }

  public constructor(private applicationNameValidator: ApplicationNameValidator) {}

  public $onChanges(): void {
    this.applicationNameValidator.validate(this.name, this.cloudProviders).then(r => this.messages = r);
  }
}

class ApplicationNameValidationMessagesComponent implements ng.IComponentOptions {
  public bindings: any = {
    name: '<',
    cloudProviders: '<',
  };
  public controller: any = ApplicationNameValidationMessagesController;
  public template = `
    <div class="form-group row slide-in" ng-if="$ctrl.messages.warnings.length">
      <div class="col-sm-9 col-sm-offset-3 warn-message" ng-repeat="warning in $ctrl.messages.warnings">
        <cloud-provider-logo provider="warning.cloudProvider" height="'16px'" width="'16px'"></cloud-provider-logo>
        {{warning.message}}
      </div>
    </div>
    <div class="form-group row slide-in" ng-if="$ctrl.messages.errors.length">
      <div class="col-sm-9 col-sm-offset-3 error-message" ng-repeat="error in $ctrl.messages.errors">
        <cloud-provider-logo provider="error.cloudProvider" height="'16px'" width="'16px'"></cloud-provider-logo>
        {{error.message}}
      </div>
    </div>
  `;
}

export const APPLICATION_NAME_VALIDATION_MESSAGES = 'spinnaker.core.application.applicationNameValidationMessages';

module(APPLICATION_NAME_VALIDATION_MESSAGES, [APPLICATION_NAME_VALIDATOR])
  .component('applicationNameValidationMessages', new ApplicationNameValidationMessagesComponent());
