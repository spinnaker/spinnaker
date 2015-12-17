'use strict';

describe('Directives: checkmap', function () {
  require('./checkmap.directive.html');

  beforeEach(
    window.module(
      require('./checkmap.directive.js')
    )
  );

  beforeEach(window.inject(function ($rootScope, $compile) {
    this.scope = $rootScope.$new();
    this.compile = $compile;
  }));

  it('updates selections when changed', function() {
    var scope = this.scope,
      compile = this.compile;

    scope.itemMap = {
      alphabet : ['a', 'b', 'c', 'd']
    };

    scope.selectedItems = ['a', 'b', 'c'];

    var checkmap = compile('<checkmap item-map="itemMap" selected-items="selectedItems"></checkmap>')(scope);

    scope.$apply();

    // Initial state check.
    expect(checkmap.find('li.checkmap-header').size()).toBe(1);
    expect(checkmap.find('input').size()).toBe(4);
    expect(checkmap.find('input:checked').size()).toBe(3);

    scope.selectedItems = ['b', 'd'];

    scope.$digest();

    expect(checkmap.find('input').size()).toBe(4);
    expect(checkmap.find('input:checked').size()).toBe(2);

    checkmap.find('input:checked')[0].click();
    scope.$digest();

    expect(checkmap.find('input:checked').size()).toBe(1);
    expect(scope.selectedItems).toEqual(['d']);

  });
});
