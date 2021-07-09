'use strict';

import * as angular from 'angular';
import { CACHE_INITIALIZER_SERVICE } from '../cache/cacheInitializer.service';

describe('Directives: regionSelectField', function () {
  beforeEach(function () {
    window.module(require('./regionSelectField.directive').name, CACHE_INITIALIZER_SERVICE, function ($provide) {
      $provide.decorator('cacheInitializer', function () {
        return {
          initialize: angular.noop,
        };
      });
    });
  });

  beforeEach(
    window.inject(function ($rootScope, $compile) {
      this.scope = $rootScope.$new();
      this.compile = $compile;
    }),
  );

  it('updates values when regions change', function () {
    var scope = this.scope;

    scope.regions = [{ name: 'us-east-1' }];

    scope.model = { regionField: 'us-east-1', accountField: 'a' };

    var html =
      '<region-select-field regions="regions" component="model" field="regionField" account="model.accountField" provider="\'aws\'" label-columns="2"></region-select-field>';

    var elem = this.compile(html)(scope);
    scope.$digest();

    expect(elem.find('option').length).toBe(2);

    scope.regions = [{ name: 'us-east-1' }, { name: 'us-west-1' }];
    scope.$digest();

    var options = elem.find('option');
    var expected = ['', 'us-east-1', 'us-west-1'];

    expect(options.length).toBe(3);
    options.each(function (idx, option) {
      expect(option.value).toBe(expected[idx]);
    });
  });

  it('selects correct initial value', function () {
    var scope = this.scope;

    scope.regions = [{ name: 'us-east-1' }, { name: 'us-west-1' }];

    scope.model = { regionField: 'us-west-1', accountField: 'a' };

    var html =
      '<region-select-field regions="regions" component="model" field="regionField" account="model.accountField" provider="\'aws\'" label-columns="2"></region-select-field>';

    var elem = this.compile(html)(scope);
    scope.$digest();

    var options = elem.find('option');
    expect(options[2].selected).toBe(true);
  });
});
