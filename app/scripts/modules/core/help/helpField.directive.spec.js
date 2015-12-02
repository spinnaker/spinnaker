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

describe('Directives: helpField', function () {

  require('./helpField.directive.html');

  var helpContentsRegistry;

  beforeEach(function() {
    window.module(
      require('./helpField.directive.js'),
      function($provide) {
        $provide.constant('helpContents', {'aws.serverGroup.stack': 'expected stack help'});
      });
  });

  beforeEach(window.inject(function ($rootScope, $compile, _helpContentsRegistry_) {
    helpContentsRegistry = _helpContentsRegistry_;
    this.executeTest = function executeTest(htmlString, expected, attr='uib-popover') {
      var $scope = $rootScope.$new();
      var helpField = $compile(htmlString)($scope);
      $scope.$digest();
      expect(helpField.find('a').attr(attr)).toBe(expected);
    };
  }));

  it('uses provided content if supplied', function() {
    this.executeTest('<help-field content="some content"></help-field>', 'some content');
  });

  it('uses key to look up content if supplied', function() {
    this.executeTest('<help-field key="aws.serverGroup.stack"></help-field>', 'expected stack help');
  });

  it('prefers overrides', function() {
    spyOn(helpContentsRegistry, 'getHelpField').and.returnValue('override content');
    this.executeTest('<help-field key="aws.serverGroup.stack"></help-field>', 'override content');
  });


  it('uses fallback if key not present', function() {
    this.executeTest('<help-field key="nonexistent.key" fallback="the fallback"></help-field>', 'the fallback');
  });

  it('ignores key if content is defined', function() {
    this.executeTest('<help-field key="aws.serverGroup.stack" content="overridden!"></help-field>', 'overridden!');
  });

  it('ignores key and fallback if content is defined', function() {
    this.executeTest('<help-field key="aws.serverGroup.stack" fallback="will be ignored" content="overridden!"></help-field>', 'overridden!');
  });

  it('defaults position to "top"', function() {
    this.executeTest('<help-field content="overridden!"></help-field>', 'top', 'popover-placement');
  });

  it('overrides position to "left"', function() {
    this.executeTest('<help-field content="some content" placement="left"></help-field>', 'left', 'popover-placement');
  });

});
