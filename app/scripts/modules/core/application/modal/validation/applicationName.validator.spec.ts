import {ApplicationNameValidator, IApplicationNameValidationResult, APPLICATION_NAME_VALIDATOR} from './applicationName.validator';
import {ExampleApplicationNameValidator, ExampleApplicationNameValidator2, EXAMPLE_APPLICATION_NAME_VALIDATOR} from './exampleApplicationName.validator';

describe('Validator: applicationName', () => {

  let validator: ApplicationNameValidator,
      validator1: ExampleApplicationNameValidator,
      validator2: ExampleApplicationNameValidator2;

  beforeEach(
    angular.mock.module(
      APPLICATION_NAME_VALIDATOR,
      EXAMPLE_APPLICATION_NAME_VALIDATOR
    )
  );

  beforeEach(angular.mock.inject((applicationNameValidator: ApplicationNameValidator,
                                  exampleApplicationNameValidator: ExampleApplicationNameValidator,
                                  exampleApplicationNameValidator2: ExampleApplicationNameValidator2) => {
    validator = applicationNameValidator;
    validator1 = exampleApplicationNameValidator;
    validator2 = exampleApplicationNameValidator2;
  }));

  describe('warning messages', () => {
    it('aggregates warning messages when multiple or no providers are specified', () => {
      let result: IApplicationNameValidationResult = validator.validate(validator1.COMMON_WARNING_CONDITION, []);
      expect(result.warnings.length).toBe(2);
      expect(result.warnings[0].cloudProvider).toEqual(validator1.provider);
      expect(result.warnings[0].message).toEqual(validator1.COMMON_WARNING_MESSAGE);
      expect(result.warnings[1].cloudProvider).toEqual(validator2.provider);
      expect(result.warnings[1].message).toEqual(validator2.COMMON_WARNING_MESSAGE);

      result = validator.validate(validator1.COMMON_WARNING_CONDITION, [validator1.provider, validator2.provider]);
      expect(result.warnings[0].message).toEqual(validator1.COMMON_WARNING_MESSAGE);
      expect(result.warnings[0].cloudProvider).toEqual(validator1.provider);
      expect(result.warnings[1].cloudProvider).toEqual(validator2.provider);
      expect(result.warnings[1].message).toEqual(validator2.COMMON_WARNING_MESSAGE);
    });

    it('provides warnings only from provider when specified', () => {
      let result: IApplicationNameValidationResult = validator.validate(validator1.WARNING_CONDITION, [validator2.provider]);
      expect(result.warnings.length).toBe(0);

      result = validator.validate(validator1.WARNING_CONDITION, [validator1.provider]);
      expect(result.warnings.length).toBe(1);
      expect(result.warnings[0].cloudProvider).toEqual(validator1.provider);
      expect(result.warnings[0].message).toEqual(validator1.WARNING_MESSAGE);
    });
  });

  describe('error messages', () => {
    it('aggregates error messages when multiple or no providers are specified', () => {
      let result: IApplicationNameValidationResult = validator.validate(validator1.COMMON_ERROR_CONDITION, []);
      expect(result.errors.length).toBe(2);
      expect(result.errors[0].cloudProvider).toEqual(validator1.provider);
      expect(result.errors[0].message).toEqual(validator1.COMMON_ERROR_MESSAGE);
      expect(result.errors[1].cloudProvider).toEqual(validator2.provider);
      expect(result.errors[1].message).toEqual(validator2.COMMON_ERROR_MESSAGE);

      result = validator.validate(validator1.COMMON_ERROR_CONDITION, [validator1.provider, validator2.provider]);
      expect(result.errors[0].message).toEqual(validator1.COMMON_ERROR_MESSAGE);
      expect(result.errors[0].cloudProvider).toEqual(validator1.provider);
      expect(result.errors[1].cloudProvider).toEqual(validator2.provider);
      expect(result.errors[1].message).toEqual(validator2.COMMON_ERROR_MESSAGE);
    });

    it('provides errors only from provider when specified', () => {
      let result: IApplicationNameValidationResult = validator.validate(validator1.ERROR_CONDITION, [validator2.provider]);
      expect(result.errors.length).toBe(0);

      result = validator.validate(validator1.ERROR_CONDITION, [validator1.provider]);
      expect(result.errors.length).toBe(1);
      expect(result.errors[0].cloudProvider).toEqual(validator1.provider);
      expect(result.errors[0].message).toEqual(validator1.ERROR_MESSAGE);
    });
  });
});
