import {module} from 'angular';
import { DirectiveFactory } from 'core/utils/tsDecorators/directiveFactoryDecorator';
import {
  APPLICATION_NAME_VALIDATOR,
  ApplicationNameValidator, IApplicationNameValidationResult
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

  public constructor(private applicationNameValidator: ApplicationNameValidator, private $q: ng.IQService) {}

  public initialize() {
    this.model.$asyncValidators['validateApplicationName'] = (value: string) => {
      let deferred: ng.IDeferred<boolean> = this.$q.defer();
      this.applicationNameValidator.validate(value, this.cloudProviders)
        .then((result: IApplicationNameValidationResult) => {
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


@DirectiveFactory('applicationNameValidator')
class ValidateApplicationNameDirective implements ng.IDirective {
  restrict: string = 'A';
  controller: any = ValidateApplicationNameController;
  controllerAs: string = '$ctrl';
  require: string = 'ngModel';
  bindToController: any = {
    cloudProviders: '<',
  };

  link($scope: ng.IScope, _$element: JQuery, $attrs: IValidateNameAttrs, ctrl: ng.INgModelController) {
    const $ctrl: ValidateApplicationNameController = $scope['$ctrl'];
    $ctrl.$scope = $scope;
    $ctrl.$attrs = $attrs;
    $ctrl.model = ctrl;
    $ctrl.initialize();
  }
}

export const VALIDATE_APPLICATION_NAME = 'spinnaker.core.application.modal.validateApplicationName.component';

module(VALIDATE_APPLICATION_NAME, [APPLICATION_NAME_VALIDATOR])
  .directive('validateApplicationName', <any>ValidateApplicationNameDirective);
