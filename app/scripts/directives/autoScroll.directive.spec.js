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

describe('Directives: auto-scroll', function () {

  beforeEach(loadDeckWithoutCacheInitializer);

  function buildContainer(height) {
    return '<div style="overflow:auto; height: ' + height + 'px"></div>';
  }
  function buildAutoScroller(watchers) {
    return '<div auto-scroll="' + watchers + '"></div>';
  }
  function buildChild(height) {
    return '<div style="height: ' + height + 'px"></div>';
  }

  function buildWatchableContainer(watch, context) {
    var $ = context.$,
        compile = context.compile,
        scope = context.scope;

    var container = $(buildContainer(15)),
      autoScroller = $(buildAutoScroller(watch)),
      child = $(buildChild(50));

    autoScroller.append(child);
    container.append(autoScroller);
    container.appendTo('body');

    compile(container)(scope);
    scope.$digest();

    return container;
  }

  beforeEach(inject(function ($rootScope, $compile, $, $timeout) {
    this.scope = $rootScope.$new();
    this.compile = $compile;
    this.timeout = $timeout;
    this.$ = $;
  }));

  it ('should scroll when a watched property is changed', function() {
    var scope = this.scope;
    scope.watched = 1;
    var container = buildWatchableContainer('watched', this);
    expect(container.scrollTop()).toBe(0);

    scope.watched = 2;
    scope.$digest();
    this.timeout.flush();

    expect(container.scrollTop()).toBe(35);
  });

  it ('should scroll on deep changes', function() {
    var scope = this.scope;

    scope.watched = {
      field: 1
    };
    var container = buildWatchableContainer('watched.field', this);

    scope.watched.field = 2;
    scope.$digest();
    this.timeout.flush();

    expect(container.scrollTop()).toBe(35);
  });

  it ('should scroll on multiple changes', function() {
    var scope = this.scope;

    scope.watched = {
      field: 1
    };
    scope.other = 'a';

    var container = buildWatchableContainer('[watched.field, other]', this);

    scope.watched.field = 2;
    scope.$digest();
    this.timeout.flush();

    expect(container.scrollTop()).toBe(35);

    container.scrollTop(0);

    scope.other = 'b';
    scope.$digest();
    this.timeout.flush();
    expect(container.scrollTop()).toBe(35);
  });

  it ('should scroll after dom changes applied', function() {
    var $ = this.$,
      compile = this.compile,
      scope = this.scope;

    scope.a = 0;
    scope.b = 0;

    var container = $(buildContainer(25)),
      autoScroller = $('<div auto-scroll="[a,b]"></div>'),
      childA = $('<div ng-if="a === 1" style="height: 100px"></div>'),
      childB = $('<div ng-if="b === 1" style="height: 60px"></div>');

    autoScroller.append(childA).append(childB);
    container.append(autoScroller);
    container.appendTo('body');

    compile(container)(scope);
    scope.$digest();
    expect(container.scrollTop()).toBe(0);

    scope.a = 1;
    scope.$digest();
    this.timeout.flush();
    expect(container.scrollTop()).toBe(75);

    scope.b = 1;
    scope.$digest();
    this.timeout.flush();
    expect(container.scrollTop()).toBe(135);

    scope.a = 2;
    scope.$digest();
    this.timeout.flush();
    expect(container.scrollTop()).toBe(35);

    container.height(170);
    scope.a = 1;
    scope.$digest();
    this.timeout.flush();
    expect(container.scrollTop()).toBe(0);
  });

});
