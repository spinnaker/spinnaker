'use strict';

describe('Validator: cronExpression', function () {

  var scope, $compile, cronValidationService, $q;

  beforeEach(
    window.module(
      require('./cron.validator.directive')
    )
  );

  // https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$q
  beforeEach(
    window.module(($qProvider) => {
      $qProvider.errorOnUnhandledRejections(false);
  }));

  beforeEach(window.inject(function ($rootScope, _$compile_, _cronValidationService_, _$q_) {
    scope = $rootScope.$new();
    $compile = _$compile_;
    cronValidationService = _cronValidationService_;
    $q = _$q_;
  }));

  beforeEach(function() {
    this.initialize = function(val) {
      scope.command = { expression: val };
      scope.validationMessages = {};

      var input = '<input type="text" name="cron" ng-model="command.expression" cron-validator cron-validation-messages="validationMessages"/>';
      var dom = '<form name="form">' + input + '</form>';

      $compile(dom)(scope);
      scope.$digest();
    };

    this.isValid = function() {
      return scope.form.cron.$valid;
    };
  });

  describe('successful validation', function () {
    it('converts description to lower case and adds to scope', function () {
      spyOn(cronValidationService, 'validate').and.returnValue($q.when({valid: true, description: 'Every day at 10:30 AM'}));
      this.initialize('0 0 0 1 1 1');
      scope.$digest();
      expect(scope.validationMessages.error).toBeUndefined();
      expect(scope.validationMessages.description).toBe('every day at 10:30 AM');
    });

    it('sets description to an empty string if not found', function () {
      spyOn(cronValidationService, 'validate').and.returnValue($q.when({valid: true}));
      this.initialize('0 0 0 1 1 1');
      scope.$digest();
      expect(scope.validationMessages.description).toBe('');
    });

    it('sets description to an empty string if it is already an empty string', function () {
      spyOn(cronValidationService, 'validate').and.returnValue($q.when({valid: true, description: ''}));
      this.initialize('0 0 0 1 1 1');
      scope.$digest();
      expect(scope.validationMessages.description).toBe('');
    });
  });

  describe('whitespace trimming', function () {
    it('compresses extra whitespace before sending value for validation', function () {
      spyOn(cronValidationService, 'validate').and.returnValue($q.when({valid: true}));
      this.initialize('0   0  0     0   0 1');
      scope.$digest();
      expect(cronValidationService.validate).toHaveBeenCalledWith('0 0 0 0 0 1');
    });
  });

  describe('failed validation', function () {
    it('sets error message on scope if available', function () {
      spyOn(cronValidationService, 'validate').and.returnValue($q.reject({message: 'is not valid'}));
      this.initialize('abcdefg');
      scope.$digest();
      expect(scope.validationMessages.error).toBe('is not valid');
      expect(scope.validationMessages.description).toBeUndefined();
    });

    it('sets default message on scope if none provided', function () {
      spyOn(cronValidationService, 'validate').and.returnValue($q.reject({}));
      this.initialize('abcdefg');
      scope.$digest();
      expect(scope.validationMessages.error).toBe('Error validating CRON expression');
      expect(scope.validationMessages.description).toBeUndefined();
    });
  });
});
