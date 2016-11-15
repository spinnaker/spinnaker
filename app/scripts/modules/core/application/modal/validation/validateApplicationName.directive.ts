import {module} from 'angular';
import {
  APPLICATION_NAME_VALIDATOR,
  ApplicationNameValidator
} from 'core/application/modal/validation/applicationName.validator';

interface IValidateNameAttrs  {
  cloudProviders: string;
}

/**
 * This component is responsible for setting the validity of the name field when creating a new application.
 * It does NOT render the error/warning messages to the screen - that is handled by the
 * applicationNameValidationMessages component.
 */

class ValidateApplicationNameController implements ng.IComponentController {

  public model: ng.INgModelController;
  public cloudProviders: string[];
  public $attrs: IValidateNameAttrs;
  public $scope: ng.IScope;

  static get $inject() { return ['applicationNameValidator']; }

  public constructor(private applicationNameValidator: ApplicationNameValidator) {}

  public initialize() {
    this.model.$validators['validateApplicationName'] = (value: string) => {
      return this.applicationNameValidator.validate(value, this.cloudProviders).errors.length === 0;
    };
    this.$scope.$watch(this.$attrs.cloudProviders, () => this.model.$validate());
  }
}

export const VALIDATE_APPLICATION_NAME = 'spinnaker.core.application.modal.validateApplicationName.component';

module(VALIDATE_APPLICATION_NAME, [APPLICATION_NAME_VALIDATOR])
  .directive('validateApplicationName', [() => {
    return {
      restrict: 'A',
      controller: ValidateApplicationNameController,
      controllerAs: '$ctrl',
      require: 'ngModel',
      bindToController: {
        cloudProviders: '<',
      },
      link: ($scope: ng.IScope, $element: JQuery, $attrs: IValidateNameAttrs, ctrl: ng.INgModelController) => {
        const $ctrl: ValidateApplicationNameController = $scope['$ctrl'];
        $ctrl.$scope = $scope;
        $ctrl.$attrs = $attrs;
        $ctrl.model = ctrl;
        $ctrl.initialize();
      }
    };
  }]);
