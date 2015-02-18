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

  beforeEach(loadDeckWithoutCacheInitializer);

  beforeEach(inject(function ($rootScope, $compile, $) {
    this.scope = $rootScope.$new();
    this.compile = $compile;
    this.$ = $;
  }));

  it('initializes with provided values', function() {
    var scope = this.scope,
        compile = this.compile;

    scope.model = {
      selections: ['a','b','c']
    };

    scope.items = ['a','b','c','d'];

    var checklist = compile('<checklist model="model.selections" items="items"></checklist>')(scope);

    scope.$digest();

    expect(checklist.find('input').size()).toBe(4);
    expect(checklist.find('input:checked').size()).toBe(3);
  });

  it('updates selections, model when model or items change externally', function() {
    var scope = this.scope,
      compile = this.compile;

    scope.model = {
      selections: ['a','b','c']
    };

    scope.items = ['a','b','c','d'];

    var checklist = compile('<checklist model="model.selections" items="items"></checklist>')(scope);

    scope.$digest();

    expect(checklist.find('input').size()).toBe(4);
    expect(checklist.find('input:checked').size()).toBe(3);

    scope.model.selections = ['b', 'd'];

    scope.$digest();

    expect(checklist.find('input').size()).toBe(4);
    expect(checklist.find('input:checked').size()).toBe(2);

    scope.items = ['a','b','c'];

    scope.$digest();

    expect(checklist.find('input').size()).toBe(3);
    expect(checklist.find('input:checked').size()).toBe(1);
    expect(scope.model.selections).toEqual(['b']);
  });

});
