import {mock} from 'angular';

import {ApplicationNameValidator, IApplicationNameValidationResult, APPLICATION_NAME_VALIDATOR} from './applicationName.validator';
import {ExampleApplicationNameValidator, ExampleApplicationNameValidator2, EXAMPLE_APPLICATION_NAME_VALIDATOR} from './exampleApplicationName.validator';
import {AccountService} from 'core/account/account.service';

describe('Validator: applicationName', () => {

  let validator: ApplicationNameValidator,
      validator1: ExampleApplicationNameValidator,
      validator2: ExampleApplicationNameValidator2,
      $scope: ng.IScope;

  beforeEach(
    mock.module(
      APPLICATION_NAME_VALIDATOR,
      EXAMPLE_APPLICATION_NAME_VALIDATOR
    )
  );

  beforeEach(mock.inject((applicationNameValidator: ApplicationNameValidator,
                          exampleApplicationNameValidator: ExampleApplicationNameValidator,
                          exampleApplicationNameValidator2: ExampleApplicationNameValidator2,
                          $rootScope: ng.IRootScopeService,
                          accountService: AccountService,
                          $q: ng.IQService) => {
    validator = applicationNameValidator;
    validator1 = exampleApplicationNameValidator;
    validator2 = exampleApplicationNameValidator2;
    $scope = $rootScope;
    spyOn(accountService, 'listProviders').and.returnValue($q.when([validator1.provider, validator2.provider]));
  }));

  describe('warning messages', () => {
    it('aggregates warning messages when multiple or no providers are specified', () => {
      let result: IApplicationNameValidationResult = null;
      validator.validate(validator1.COMMON_WARNING_CONDITION, [])
        .then(r => result = r);
      $scope.$digest();
      expect(result.warnings.length).toBe(2);
      expect(result.warnings[0].cloudProvider).toEqual(validator1.provider);
      expect(result.warnings[0].message).toEqual(validator1.COMMON_WARNING_MESSAGE);
      expect(result.warnings[1].cloudProvider).toEqual(validator2.provider);
      expect(result.warnings[1].message).toEqual(validator2.COMMON_WARNING_MESSAGE);

      validator.validate(validator1.COMMON_WARNING_CONDITION, [validator1.provider, validator2.provider])
        .then(r => result = r);
      $scope.$digest();
      expect(result.warnings[0].message).toEqual(validator1.COMMON_WARNING_MESSAGE);
      expect(result.warnings[0].cloudProvider).toEqual(validator1.provider);
      expect(result.warnings[1].cloudProvider).toEqual(validator2.provider);
      expect(result.warnings[1].message).toEqual(validator2.COMMON_WARNING_MESSAGE);
    });

    it('provides warnings only from provider when specified', () => {
      let result: IApplicationNameValidationResult = null;
      validator.validate(validator1.WARNING_CONDITION, [validator2.provider])
        .then(r => result = r);
      $scope.$digest();
      expect(result.warnings.length).toBe(0);

      validator.validate(validator1.WARNING_CONDITION, [validator1.provider])
        .then(r => result = r);
      $scope.$digest();
      expect(result.warnings.length).toBe(1);
      expect(result.warnings[0].cloudProvider).toEqual(validator1.provider);
      expect(result.warnings[0].message).toEqual(validator1.WARNING_MESSAGE);
    });
  });

  describe('error messages', () => {
    it('aggregates error messages when multiple or no providers are specified', () => {
      let result: IApplicationNameValidationResult = null;
      validator.validate(validator1.COMMON_ERROR_CONDITION, [])
        .then(r => result = r);
      $scope.$digest();
      expect(result.errors.length).toBe(2);
      expect(result.errors[0].cloudProvider).toEqual(validator1.provider);
      expect(result.errors[0].message).toEqual(validator1.COMMON_ERROR_MESSAGE);
      expect(result.errors[1].cloudProvider).toEqual(validator2.provider);
      expect(result.errors[1].message).toEqual(validator2.COMMON_ERROR_MESSAGE);

      validator.validate(validator1.COMMON_ERROR_CONDITION, [validator1.provider, validator2.provider])
        .then(r => result = r);
      $scope.$digest();
      expect(result.errors[0].message).toEqual(validator1.COMMON_ERROR_MESSAGE);
      expect(result.errors[0].cloudProvider).toEqual(validator1.provider);
      expect(result.errors[1].cloudProvider).toEqual(validator2.provider);
      expect(result.errors[1].message).toEqual(validator2.COMMON_ERROR_MESSAGE);
    });

    it('provides errors only from provider when specified', () => {
      let result: IApplicationNameValidationResult = null;
      validator.validate(validator1.ERROR_CONDITION, [validator2.provider])
        .then(r => result = r);
      $scope.$digest();
      expect(result.errors.length).toBe(0);

      validator.validate(validator1.ERROR_CONDITION, [validator1.provider])
        .then(r => result = r);
      $scope.$digest();
      expect(result.errors.length).toBe(1);
      expect(result.errors[0].cloudProvider).toEqual(validator1.provider);
      expect(result.errors[0].message).toEqual(validator1.ERROR_MESSAGE);
    });
  });
});
