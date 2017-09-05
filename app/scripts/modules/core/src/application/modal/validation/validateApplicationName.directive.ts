import { IAttributes, IController, IDeferred, IDirective, INgModelController, IQService, IScope, module } from 'angular';
import { DirectiveFactory } from 'core/utils/tsDecorators/directiveFactoryDecorator';
import {
  APPLICATION_NAME_VALIDATOR,
  ApplicationNameValidator, IApplicationNameValidationResult
} from 'core/application/modal/validation/applicationName.validator';

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

  public constructor(private applicationNameValidator: ApplicationNameValidator, private $q: IQService) {}

  public initialize() {
    this.model.$asyncValidators['validateApplicationName'] = (value: string) => {
      const deferred: IDeferred<boolean> = this.$q.defer();
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
class ValidateApplicationNameDirective implements IDirective {
  public restrict = 'A';
  public controller: any = ValidateApplicationNameController;
  public controllerAs = '$ctrl';
  public require = 'ngModel';
  public bindToController: any = {
    cloudProviders: '<',
  };

  public link($scope: IScope, _$element: JQuery, $attrs: IValidateNameAttrs, ctrl: INgModelController) {
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
