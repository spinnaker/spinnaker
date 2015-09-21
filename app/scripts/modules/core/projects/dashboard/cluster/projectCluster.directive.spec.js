'use strict';

describe('Directives: projectCluster', function () {

  require('./projectCluster.directive.html');

  beforeEach(
    window.module(
      require('./projectCluster.directive.js')
    )
  );

  beforeEach(window.inject(function ($rootScope, $compile, $httpBackend) {
    this.scope = $rootScope.$new();
    this.scope.project = {};
    this.scope.cluster = {};
    this.compile = $compile;
    this.$http = $httpBackend;
  }));

  describe('something', function() {

    it('does something', function () {
      var domNode = this.compile('<health-counts container="container"></health-counts>')(this.scope);
      this.scope.$digest();

      expect(domNode.find('span').size()).toBe(0);
    });

    it('does something else', function () {
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
  });

});
