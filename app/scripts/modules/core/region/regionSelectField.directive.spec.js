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

describe('Directives: regionSelectField', function () {

  const angular = require('angular');

  require('./regionSelectField.directive.html');

  beforeEach(function() {
    window.module(
      require('./regionSelectField.directive.js'),
      require('../cache/cacheInitializer.js'),
      function($provide) {
        $provide.decorator('cacheInitializer', function() {
          return {
            initialize: angular.noop
          };
        });
      }
    );
  });

  beforeEach(window.inject(function ($rootScope, $compile) {
    this.scope = $rootScope.$new();
    this.compile = $compile;
  }));

  it('updates values when regions change', function() {
    var scope = this.scope;

    scope.regions = [{name: 'us-east-1'}];

    scope.model = { regionField: 'us-east-1', accountField: 'a'};

    var html = '<region-select-field regions="regions" component="model" field="regionField" account="model.accountField" provider="\'aws\'" label-columns="2"></region-select-field>';

    var elem = this.compile(html)(scope);
    scope.$digest();

    expect(elem.find('option').length).toBe(1);

    scope.regions = [{name: 'us-east-1'}, {name: 'us-west-1'}];
    scope.$digest();

    var options = elem.find('option');
    var expected = ['us-east-1', 'us-west-1'];

    expect(options.length).toBe(2);
    options.each(function(idx, option) {
      expect(option.value).toBe(expected[idx]);
    });
  });

  it('selects correct initial value', function () {
    var scope = this.scope;

    scope.regions = [{name: 'us-east-1'}, {name: 'us-west-1'}];

    scope.model = { regionField: 'us-west-1', accountField: 'a'};

    var html = '<region-select-field regions="regions" component="model" field="regionField" account="model.accountField" provider="\'aws\'" label-columns="2"></region-select-field>';

    var elem = this.compile(html)(scope);
    scope.$digest();

    var options = elem.find('option');
    expect(options[1].selected).toBe(true);

  });
});
