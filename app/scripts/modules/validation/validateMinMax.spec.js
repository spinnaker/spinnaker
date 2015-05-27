/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

describe('Directives: validateMin, validateMax', function () {

  beforeEach(module('spinnaker.validation'));

  beforeEach(inject(function ($rootScope, $compile) {
    this.scope = $rootScope.$new();
    this.compile = $compile;
  }));

  describe('validateMin', function() {

    beforeEach(function() {
      this.scope.model = {
        numericField: 6,
        minField: 5
      };

      this.input = this.compile('<input type="number" ng-model="model.numericField" validate-min="model.minField"/>')(this.scope);

      this.enterValue = function enterValue(newVal) {
        this.input.val(newVal).trigger('change');
        this.scope.$digest();
      };

      this.expectFieldValidity = function expectFieldValidity(isValid) {
        if (!isValid) {
          expect(this.input.hasClass('ng-invalid-validate-min')).toBe(true);
        } else {
          expect(this.input.hasClass('ng-valid')).toBe(true);
        }
      };
    });

    it('pushes value back to model, even when invalid', function() {
      this.enterValue(3);
      this.expectFieldValidity(false);
      expect(this.scope.model.numericField).toBe(3);
    });

    it('boundary checks', function() {
      this.enterValue(4);
      this.expectFieldValidity(false);

      this.enterValue(5);
      this.expectFieldValidity(true);

      this.enterValue(6);
      this.expectFieldValidity(true);
    });

    it('sets validity when other field changes', function() {
      var scope = this.scope,
          model = scope.model;

      model.minField = 7;
      scope.$digest();
      this.expectFieldValidity(false);

      model.minField = 2;
      scope.$digest();
      this.expectFieldValidity(true);
    });
  });

  describe('validateMax', function() {
    beforeEach(function() {
      this.scope.model = {
        numericField: 6,
        maxField: 7
      };

      this.input = this.compile('<input type="number" ng-model="model.numericField" validate-max="model.maxField"/>')(this.scope);

      this.enterValue = function enterValue(newVal) {
        this.input.val(newVal).trigger('change');
        this.scope.$digest();
      };

      this.expectFieldValidity = function expectFieldValidity(isValid) {
        if (!isValid) {
          expect(this.input.hasClass('ng-invalid-validate-max')).toBe(true);
        } else {
          expect(this.input.hasClass('ng-valid')).toBe(true);
        }
      }
    });

    it('pushes value back to model, even when invalid', function() {
      this.enterValue(8);
      this.expectFieldValidity(false);
      expect(this.scope.model.numericField).toBe(8);
    });

    it('boundary checks', function() {
      this.enterValue(8);
      this.expectFieldValidity(false);

      this.enterValue(7);
      this.expectFieldValidity(true);

      this.enterValue(6);
      this.expectFieldValidity(true);
    });

    it('sets validity when other field changes', function() {
      var scope = this.scope,
        model = scope.model;

      model.maxField = 5;
      scope.$digest();
      this.expectFieldValidity(false);

      model.maxField = 12;
      scope.$digest();
      this.expectFieldValidity(true);
    });
  });

});
