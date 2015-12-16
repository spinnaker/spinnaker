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

describe('Directives: checklist', function () {

  require('./checklist.directive.html');

  beforeEach(
    window.module(
      require('./checklist.directive.js')
    )
  );

  beforeEach(window.inject(function ($rootScope, $compile) {
    this.scope = $rootScope.$new();
    this.compile = $compile;
  }));

  it('initializes with provided values', function() {
    var scope = this.scope,
        compile = this.compile;

    scope.model = {
      selections: ['a', 'b', 'c']
    };

    scope.items = ['a', 'b', 'c', 'd'];

    var checklist = compile('<checklist model="model.selections" items="items"></checklist>')(scope);

    scope.$digest();

    expect(checklist.find('input').size()).toBe(4);
    expect(checklist.find('input:checked').size()).toBe(3);

  });

  it('updates selections, model when model or items change externally', function() {
    var scope = this.scope,
      compile = this.compile;

    scope.model = {
      selections: ['a', 'b', 'c']
    };

    scope.items = ['a', 'b', 'c', 'd'];

    var checklist = compile('<checklist model="model.selections" items="items" inline="true"></checklist>')(scope);

    scope.$apply();

    expect(checklist.find('input').size()).toBe(4);
    expect(checklist.find('input:checked').size()).toBe(3);

    scope.model.selections = ['b', 'd'];

    scope.$digest();

    expect(checklist.find('input').size()).toBe(4);
    expect(checklist.find('input:checked').size()).toBe(2);

    scope.items = ['a', 'b', 'c'];

    scope.$digest();

    expect(checklist.find('input').size()).toBe(3);
    expect(checklist.find('input:checked').size()).toBe(1);
    expect(scope.model.selections).toEqual(['b']);
  });

  it('selects all items when clicking "Select All"', function() {
    var scope = this.scope,
        compile = this.compile;

    scope.model = {
      selections: ['a', 'b', 'c']
    };

    scope.items = ['a', 'b', 'c', 'd'];

    var html = '<checklist model="model.selections" items="items" include-select-all-button="true"></checklist>';
    var checklist = compile(html)(scope);
    scope.$digest();

    expect(checklist.find('input:checked').size()).toBe(3);
    $(checklist.find('a')[0]).click();
    expect(checklist.find('input:checked').size()).toBe(4);
  });

  it('deselects all items when clicking "Deselect All"', function() {
    var scope = this.scope,
        compile = this.compile;

    scope.model = {
      selections: ['a', 'b', 'c', 'd']
    };

    scope.items = ['a', 'b', 'c', 'd'];

    var html = '<checklist model="model.selections" items="items" inline="true" include-select-all-button="true"></checklist>';
    var checklist = compile(html)(scope);
    scope.$digest();

    expect(checklist.find('input:checked').size()).toBe(4);
    $(checklist.find('a')[0]).click();
    expect(checklist.find('input:checked').size()).toBe(0);
  });

  it('shows correct text for "Deselect" or "Select" based on current selection', function() {
    var scope = this.scope,
        compile = this.compile;

    scope.model = {
      selections: ['a', 'b', 'c']
    };

    scope.items = ['a', 'b', 'c', 'd'];

    var html = '<checklist model="model.selections" items="items" include-select-all-button="true"></checklist>';
    var checklist = compile(html)(scope);
    scope.$digest();

    var selectButton = checklist.find('a')[0];

    expect(selectButton.text).toBe('Select All'); // Some items selected
    $(selectButton).click();
    expect(selectButton.text).toBe('Deselect All'); // All items selected
    $(selectButton).click();
    expect(selectButton.text).toBe('Select All'); // No items selected
  });
});
