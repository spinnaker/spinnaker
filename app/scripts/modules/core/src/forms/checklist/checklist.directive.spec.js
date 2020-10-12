'use strict';

describe('Directives: checklist', function () {
  require('./checklist.directive.html');

  beforeEach(window.module(require('./checklist.directive').name));

  beforeEach(
    window.inject(function ($rootScope, $compile) {
      this.scope = $rootScope.$new();
      this.compile = $compile;
    }),
  );

  it('initializes with provided values', function () {
    var scope = this.scope,
      compile = this.compile;

    scope.model = {
      selections: ['a', 'b', 'c'],
    };

    scope.items = ['a', 'b', 'c', 'd'];

    var checklist = compile('<checklist model="model.selections" items="items"></checklist>')(scope);

    scope.$digest();

    expect(checklist.find('input').length).toBe(4);
    expect(checklist.find('input:checked').length).toBe(3);
  });

  it('updates selections, model when model or items change externally', function () {
    var scope = this.scope,
      compile = this.compile;

    scope.model = {
      selections: ['a', 'b', 'c'],
    };

    scope.items = ['a', 'b', 'c', 'd'];

    var checklist = compile('<checklist model="model.selections" items="items" inline="true"></checklist>')(scope);

    scope.$apply();

    expect(checklist.find('input').length).toBe(4);
    expect(checklist.find('input:checked').length).toBe(3);

    scope.model.selections = ['b', 'd'];

    scope.$digest();

    expect(checklist.find('input').length).toBe(4);
    expect(checklist.find('input:checked').length).toBe(2);

    scope.items = ['a', 'b', 'c'];

    scope.$digest();

    expect(checklist.find('input').length).toBe(3);
    expect(checklist.find('input:checked').length).toBe(1);
    expect(scope.model.selections).toEqual(['b']);
  });

  it('selects all items when clicking "Select All"', function () {
    var scope = this.scope,
      compile = this.compile;

    scope.model = {
      selections: ['a', 'b', 'c'],
    };

    scope.items = ['a', 'b', 'c', 'd'];

    var html = '<checklist model="model.selections" items="items" include-select-all-button="true"></checklist>';
    var checklist = compile(html)(scope);
    scope.$digest();

    expect(checklist.find('input:checked').length).toBe(3);
    $(checklist.find('a')[0]).click();
    expect(checklist.find('input:checked').length).toBe(4);
  });

  it('deselects all items when clicking "Deselect All"', function () {
    var scope = this.scope,
      compile = this.compile;

    scope.model = {
      selections: ['a', 'b', 'c', 'd'],
    };

    scope.items = ['a', 'b', 'c', 'd'];

    var html =
      '<checklist model="model.selections" items="items" inline="true" include-select-all-button="true"></checklist>';
    var checklist = compile(html)(scope);
    scope.$digest();

    expect(checklist.find('input:checked').length).toBe(4);
    $(checklist.find('a')[0]).click();
    expect(checklist.find('input:checked').length).toBe(0);
  });

  it('shows correct text for "Deselect" or "Select" based on current selection', function () {
    var scope = this.scope,
      compile = this.compile;

    scope.model = {
      selections: ['a', 'b', 'c'],
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

  it('supports using Map (for key/value pairs) as items', function () {
    const scope = this.scope,
      compile = this.compile;

    scope.model = {
      selections: ['a', 'c'],
    };

    scope.items = new Map([
      ['a', 'x'],
      ['b', 'y'],
      ['c', 'z'],
    ]);

    const checklist = compile('<checklist model="model.selections" items="items"></checklist>')(scope);

    scope.$digest();

    expect(checklist.find('input').length).toBe(3);
    expect(checklist.find('input:checked').length).toBe(2);
    expect(
      checklist
        .find('input')
        .parent()
        .map((index, element) => $(element).text().trim())
        .get(),
    ).toEqual(['x', 'y', 'z']);
  });
});
