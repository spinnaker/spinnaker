import { mock } from 'angular';

import { ApplicationNameValidator, IApplicationNameValidationResult } from './ApplicationNameValidator';
import { ExampleApplicationNameValidator, ExampleApplicationNameValidator2 } from './ExampleApplicationNameValidator';
import { AccountService } from '../../../account/AccountService';

describe('Validator: applicationName', () => {
  const validator1 = new ExampleApplicationNameValidator();
  const validator2 = new ExampleApplicationNameValidator2();
  let $scope: ng.IScope;

  beforeEach(
    mock.inject(($rootScope: ng.IRootScopeService, $q: ng.IQService) => {
      $scope = $rootScope;
      spyOn(AccountService, 'listProviders').and.returnValue($q.when([validator1.provider, validator2.provider]));
    }),
  );

  describe('warning messages', () => {
    it('aggregates warning messages when multiple or no providers are specified', () => {
      let result: IApplicationNameValidationResult = null;
      ApplicationNameValidator.validate(validator1.COMMON_WARNING_CONDITION, []).then((r) => (result = r));
      $scope.$digest();
      expect(result.warnings.length).toBe(2);
      expect(result.warnings[0].cloudProvider).toEqual(validator1.provider);
      expect(result.warnings[0].message).toEqual(validator1.COMMON_WARNING_MESSAGE);
      expect(result.warnings[1].cloudProvider).toEqual(validator2.provider);
      expect(result.warnings[1].message).toEqual(validator2.COMMON_WARNING_MESSAGE);

      ApplicationNameValidator.validate(validator1.COMMON_WARNING_CONDITION, [
        validator1.provider,
        validator2.provider,
      ]).then((r) => (result = r));
      $scope.$digest();
      expect(result.warnings[0].message).toEqual(validator1.COMMON_WARNING_MESSAGE);
      expect(result.warnings[0].cloudProvider).toEqual(validator1.provider);
      expect(result.warnings[1].cloudProvider).toEqual(validator2.provider);
      expect(result.warnings[1].message).toEqual(validator2.COMMON_WARNING_MESSAGE);
    });

    it('provides warnings only from provider when specified', () => {
      let result: IApplicationNameValidationResult = null;
      ApplicationNameValidator.validate(validator1.WARNING_CONDITION, [validator2.provider]).then((r) => (result = r));
      $scope.$digest();
      expect(result.warnings.length).toBe(0);

      ApplicationNameValidator.validate(validator1.WARNING_CONDITION, [validator1.provider]).then((r) => (result = r));
      $scope.$digest();
      expect(result.warnings.length).toBe(1);
      expect(result.warnings[0].cloudProvider).toEqual(validator1.provider);
      expect(result.warnings[0].message).toEqual(validator1.WARNING_MESSAGE);
    });
  });

  describe('error messages', () => {
    it('aggregates error messages when multiple or no providers are specified', () => {
      let result: IApplicationNameValidationResult = null;
      ApplicationNameValidator.validate(validator1.COMMON_ERROR_CONDITION, []).then((r) => (result = r));
      $scope.$digest();
      expect(result.errors.length).toBe(2);
      expect(result.errors[0].cloudProvider).toEqual(validator1.provider);
      expect(result.errors[0].message).toEqual(validator1.COMMON_ERROR_MESSAGE);
      expect(result.errors[1].cloudProvider).toEqual(validator2.provider);
      expect(result.errors[1].message).toEqual(validator2.COMMON_ERROR_MESSAGE);

      ApplicationNameValidator.validate(validator1.COMMON_ERROR_CONDITION, [
        validator1.provider,
        validator2.provider,
      ]).then((r) => (result = r));
      $scope.$digest();
      expect(result.errors[0].message).toEqual(validator1.COMMON_ERROR_MESSAGE);
      expect(result.errors[0].cloudProvider).toEqual(validator1.provider);
      expect(result.errors[1].cloudProvider).toEqual(validator2.provider);
      expect(result.errors[1].message).toEqual(validator2.COMMON_ERROR_MESSAGE);
    });

    it('provides errors only from provider when specified', () => {
      let result: IApplicationNameValidationResult = null;
      ApplicationNameValidator.validate(validator1.ERROR_CONDITION, [validator2.provider]).then((r) => (result = r));
      $scope.$digest();
      expect(result.errors.length).toBe(0);

      ApplicationNameValidator.validate(validator1.ERROR_CONDITION, [validator1.provider]).then((r) => (result = r));
      $scope.$digest();
      expect(result.errors.length).toBe(1);
      expect(result.errors[0].cloudProvider).toEqual(validator1.provider);
      expect(result.errors[0].message).toEqual(validator1.ERROR_MESSAGE);
    });
  });
});
