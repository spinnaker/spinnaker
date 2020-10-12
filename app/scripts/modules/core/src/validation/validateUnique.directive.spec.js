'use strict';

describe('Validator: validateUnique', function () {
  beforeEach(window.module(require('./validateUnique.directive').name));

  beforeEach(
    window.inject(function ($rootScope, $compile, $controller) {
      this.scope = $rootScope.$new();
      this.compile = $compile;
      this.$controller = $controller;
    }),
  );

  beforeEach(function () {
    var scope = this.scope,
      compile = this.compile;

    this.initialize = function (val, options, ignoreCase) {
      scope.command = { field: val };
      scope.options = options;

      var input = '<input type="text" name="firstName" ng-model="command.field" validate-unique="options"';
      if (ignoreCase) {
        input += ' validate-ignore-case="true"';
      }
      input += '/>';

      var dom = '<form name="form">' + input + '</form>';

      compile(dom)(scope);
      scope.$digest();
    };

    this.isValid = function () {
      return scope.form.firstName.$valid;
    };
  });

  describe('valid cases', function () {
    it('should be valid when no options present', function () {
      this.initialize('joe', null);
      expect(this.isValid()).toBe(true);
    });

    it('should be valid when options are empty', function () {
      this.initialize('joe', []);
      expect(this.isValid()).toBe(true);
    });

    it('should be valid when options do not include value', function () {
      this.initialize('joe', ['mike']);
      expect(this.isValid()).toBe(true);
    });

    it('should be valid when case does not match', function () {
      this.initialize('joe', ['Joe']);
      expect(this.isValid()).toBe(true);
    });
  });

  describe('invalid cases', function () {
    it('should be invalid when value is present', function () {
      this.initialize('joe', ['joe']);
      expect(this.isValid()).toBe(false);
    });

    it('should be invalid when ignore case sensitive flag is set', function () {
      this.initialize('joe', ['Joe'], true);
      expect(this.isValid()).toBe(false);
    });
  });

  describe('value/option changes', function () {
    it('should flip to false when options change and include set value', function () {
      this.initialize('joe', ['mike']);
      expect(this.isValid()).toBe(true);

      this.scope.options.push('joe');
      this.scope.$digest();
      expect(this.isValid()).toBe(false);
    });

    it('should flip to true when options change and remove set value', function () {
      this.initialize('joe', ['mike', 'joe']);
      expect(this.isValid()).toBe(false);

      this.scope.options.pop();
      this.scope.$digest();
      expect(this.isValid()).toBe(true);
    });

    it('should flip to false when value changes and is in options', function () {
      this.initialize('joe', ['mike']);
      expect(this.isValid()).toBe(true);

      this.scope.command.field = 'mike';
      this.scope.$digest();
      expect(this.isValid()).toBe(false);
    });

    it('should flip to true when value changes and is not in options', function () {
      this.initialize('mike', ['mike']);
      expect(this.isValid()).toBe(false);

      this.scope.command.field = 'joe';
      this.scope.$digest();
      expect(this.isValid()).toBe(true);
    });
  });
});
