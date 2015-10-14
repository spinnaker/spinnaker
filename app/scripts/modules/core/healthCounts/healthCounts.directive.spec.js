'use strict';

describe('Directives: healthCounts', function () {

  require('./healthCounts.html');

  beforeEach(
    window.module(
      require('./healthCounts.directive.js')
    )
  );

  beforeEach(window.inject(function ($rootScope, $compile) {
    this.scope = $rootScope.$new();
    this.scope.container = {};
    this.compile = $compile;
  }));

  describe('health count rendering', function() {

    it('displays nothing when container has no health info', function () {
      var domNode = this.compile('<health-counts container="container"></health-counts>')(this.scope);
      this.scope.$digest();

      expect(domNode.find('span').size()).toBe(0);
    });

    it('displays up count when provided', function () {
      this.scope.container = {
        totalCount: 1,
        upCount: 1,
      };
      var domNode = this.compile('<health-counts container="container"></health-counts>')(this.scope);
      this.scope.$digest();

      expect(domNode.find('span.counter').size()).toBe(1);
      expect(domNode.find('.glyphicon-Up-triangle').size()).toBe(1);
      expect(domNode.find('.glyphicon-Down-triangle').size()).toBe(0);
      expect(domNode.find('.glyphicon-Unknown-triangle').size()).toBe(0);
      expect(domNode.find('span.healthy').size()).toBe(2);
      expect(domNode.find('span.healthy').text().trim()).toBe('100%');
    });

    it('displays up and down count when both exist', function() {
      this.scope.container = {
        totalCount: 2,
        upCount: 1,
        downCount: 1,
      };
      var domNode = this.compile('<health-counts container="container"></health-counts>')(this.scope);
      this.scope.$digest();

      expect(domNode.find('span.counter').size()).toBe(1);
      expect(domNode.find('.glyphicon-Up-triangle').size()).toBe(1);
      expect(domNode.find('.glyphicon-Down-triangle').size()).toBe(1);
      expect(domNode.find('.glyphicon-Unknown-triangle').size()).toBe(0);
      expect(domNode.find('span.healthy').size()).toBe(1);
      expect(domNode.find('span.dead').size()).toBe(1);
      expect(domNode.find('span.unhealthy').size()).toBe(1);
      expect(domNode.find('span.unhealthy').text().trim()).toBe('50%');

    });

    it('displays up and unknown count when both exist', function() {
      this.scope.container = {
        totalCount: 2,
        upCount: 1,
        unknownCount: 1,
      };
      var domNode = this.compile('<health-counts container="container"></health-counts>')(this.scope);
      this.scope.$digest();

      expect(domNode.find('span.counter').size()).toBe(1);
      expect(domNode.find('.glyphicon-Up-triangle').size()).toBe(1);
      expect(domNode.find('.glyphicon-Down-triangle').size()).toBe(0);
      expect(domNode.find('.glyphicon-Unknown-triangle').size()).toBe(1);
      expect(domNode.find('span.healthy').size()).toBe(1);
      expect(domNode.find('span.dead').size()).toBe(0);
      expect(domNode.find('span.unknown').size()).toBe(1);
      expect(domNode.find('span.unhealthy').size()).toBe(1);
      expect(domNode.find('span.unhealthy').text().trim()).toBe('50%');

    });

    it('displays all three counters', function() {
      this.scope.container = {
        totalCount: 3,
        upCount: 1,
        downCount: 1,
        unknownCount: 1,
      };
      var domNode = this.compile('<health-counts container="container"></health-counts>')(this.scope);
      this.scope.$digest();

      expect(domNode.find('span.counter').size()).toBe(1);
      expect(domNode.find('.glyphicon-Up-triangle').size()).toBe(1);
      expect(domNode.find('.glyphicon-Down-triangle').size()).toBe(1);
      expect(domNode.find('.glyphicon-Unknown-triangle').size()).toBe(1);
      expect(domNode.find('span.healthy').size()).toBe(1);
      expect(domNode.find('span.dead').size()).toBe(1);
      expect(domNode.find('span.unknown').size()).toBe(1);
      expect(domNode.find('span.unhealthy').size()).toBe(1);
      expect(domNode.find('span.unhealthy').text().trim()).toBe('33%');
    });

    it('updates DOM when counters change', function() {
      this.scope.container = {
        totalCount: 3,
        upCount: 1,
        downCount: 1,
        unknownCount: 1,
      };
      var domNode = this.compile('<health-counts container="container"></health-counts>')(this.scope);
      this.scope.$digest();

      expect(domNode.find('span.counter').size()).toBe(1);
      expect(domNode.find('.glyphicon-Up-triangle').size()).toBe(1);
      expect(domNode.find('.glyphicon-Down-triangle').size()).toBe(1);
      expect(domNode.find('.glyphicon-Unknown-triangle').size()).toBe(1);
      expect(domNode.find('span.healthy').size()).toBe(1);
      expect(domNode.find('span.dead').size()).toBe(1);
      expect(domNode.find('span.unknown').size()).toBe(1);
      expect(domNode.find('span.unhealthy').size()).toBe(1);
      expect(domNode.find('span.unhealthy').text().trim()).toBe('33%');

      this.scope.container = {
        totalCount: 4,
        upCount: 2,
        downCount: 1,
        unknownCount: 1,
      };
      this.scope.$digest();
      expect(domNode.find('span.unhealthy').text().trim()).toBe('50%');
    });
  });

});
