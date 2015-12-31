'use strict';

describe('Validator: applicationName', function () {

  var validator, validator1, validator2;

  beforeEach(
    window.module(
      require('./applicationName.validator.js'),
      require('./exampleApplicationName.validator.js')
    )
  );

  beforeEach(window.inject(function (applicationNameValidator, exampleApplicationNameValidator, exampleApplicationNameValidator2) {
    validator = applicationNameValidator;
    validator1 = exampleApplicationNameValidator;
    validator2 = exampleApplicationNameValidator2;
  }));

  describe('warning messages', function () {
    it('aggregates warning messages when multiple or no providers are specified', function () {
      var result = validator.validate(validator1.COMMON_WARNING_CONDITION, []);
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

    it('provides warnings only from provider when specified', function () {
      var result = validator.validate(validator1.WARNING_CONDITION, [validator2.provider]);
      expect(result.warnings.length).toBe(0);

      result = validator.validate(validator1.WARNING_CONDITION, [validator1.provider]);
      expect(result.warnings.length).toBe(1);
      expect(result.warnings[0].cloudProvider).toEqual(validator1.provider);
      expect(result.warnings[0].message).toEqual(validator1.WARNING_MESSAGE);
    });
  });

  describe('error messages', function () {
    it('aggregates error messages when multiple or no providers are specified', function () {
      var result = validator.validate(validator1.COMMON_ERROR_CONDITION, []);
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

    it('provides errors only from provider when specified', function () {
      var result = validator.validate(validator1.ERROR_CONDITION, [validator2.provider]);
      expect(result.errors.length).toBe(0);

      result = validator.validate(validator1.ERROR_CONDITION, [validator1.provider]);
      expect(result.errors.length).toBe(1);
      expect(result.errors[0].cloudProvider).toEqual(validator1.provider);
      expect(result.errors[0].message).toEqual(validator1.ERROR_MESSAGE);
    });
  });
});
