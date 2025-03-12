'use strict';

describe('Directives: checkmap', function () {
  require('./checkmap.directive.html');

  beforeEach(window.module(require('./checkmap.directive').name));

  beforeEach(
    window.inject(function ($rootScope, $compile, $rootElement, $document) {
      this.scope = $rootScope.$new();
      this.compile = $compile;
      this.rootElement = $rootElement;
      this.document = $document;
    }),
  );

  it('updates selections when changed', function () {
    var scope = this.scope,
      compile = this.compile,
      rootElement = this.rootElement,
      document = this.document;

    scope.itemMap = {
      alphabet: ['a', 'b', 'c', 'd'],
    };

    scope.selectedItems = ['a', 'b', 'c'];

    var checkmap = compile('<checkmap item-map="itemMap" selected-items="selectedItems"></checkmap>')(scope);

    //Angular 1.7 introduced changes to how checkboxes listen to change events. This primarily impacted tests where components which need to be clicked now need to be appended to the document
    // See https://docs.angularjs.org/guide/migration#-input-radio-and-input-checkbox-
    rootElement.append(checkmap);
    document.find('body').append(rootElement);

    scope.$apply();

    // Initial state check.
    expect(checkmap.find('li.checkmap-header').length).toBe(1);
    expect(checkmap.find('input').length).toBe(4);
    expect(checkmap.find('input:checked').length).toBe(3);

    scope.selectedItems = ['b', 'd'];

    scope.$digest();

    expect(checkmap.find('input').length).toBe(4);
    expect(checkmap.find('input:checked').length).toBe(2);

    checkmap.find('input:checked')[0].click();
    scope.$digest();

    expect(checkmap.find('input:checked').length).toBe(1);
    expect(scope.selectedItems).toEqual(['d']);
  });
});
