import { IAttributes, IController, IDeferred, INgModelController, IQService, IScope, module } from 'angular';
import { ApplicationNameValidator, IApplicationNameValidationResult } from './ApplicationNameValidator';

interface IValidateNameAttrs extends IAttributes {
  cloudProviders: string;
}

/**
 * This component is responsible for setting the validity of the name field when creating a new application.
 * It does NOT render the error/warning messages to the screen - that is handled by the
 * applicationNameValidationMessages component.
 */

class ValidateApplicationNameController implements IController {
  public model: INgModelController;
  public cloudProviders: string[];
  public $attrs: IValidateNameAttrs;
  public $scope: IScope;

  public static $inject = ['$q'];
  constructor(private $q: IQService) {}

  public initialize() {
    this.model.$asyncValidators['validateApplicationName'] = (value: string) => {
      const deferred: IDeferred<boolean> = this.$q.defer();
      ApplicationNameValidator.validate(value, this.cloudProviders).then((result: IApplicationNameValidationResult) => {
        if (result.errors.length) {
          deferred.reject();
        } else {
          deferred.resolve();
        }
      });
      return deferred.promise;
    };
    this.$scope.$watch(this.$attrs.cloudProviders, () => this.model.$validate());
  }
}

export const VALIDATE_APPLICATION_NAME = 'spinnaker.core.application.modal.validateApplicationName.component';

module(VALIDATE_APPLICATION_NAME, []).directive('validateApplicationName', function () {
  return {
    restrict: 'A',
    controller: ValidateApplicationNameController,
    controllerAs: '$ctrl',
    require: 'ngModel',
    bindToController: {
      cloudProviders: '<',
    },
    link: ($scope: IScope, _$element: JQuery, $attrs: IValidateNameAttrs, ctrl: INgModelController) => {
      const $ctrl: ValidateApplicationNameController = $scope['$ctrl'];
      $ctrl.$scope = $scope;
      $ctrl.$attrs = $attrs;
      $ctrl.model = ctrl;
      $ctrl.initialize();
    },
  };
});
