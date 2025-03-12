'use strict';

describe('Component: mapObjectEditor', function () {
  var scope;

  beforeEach(window.module(require('./mapObjectEditor.component').name));

  beforeEach(
    window.inject(function ($rootScope, $compile) {
      scope = $rootScope.$new();
      this.compile = $compile;
    }),
  );

  it('initializes with provided values', function () {
    scope.model = { foo: { bar: 'baz' }, bah: 11 };
    let dom = this.compile('<map-object-editor model="model"></map-object-editor>')(scope);
    scope.$digest();

    expect(dom.find('input').length).toBe(2);
    expect(dom.find('textarea').length).toBe(2);

    expect(dom.find('input').get(0).value).toBe('foo');
    expect(dom.find('textarea').get(0).value).toBe(JSON.stringify({ bar: 'baz' }, null, 2));
    expect(dom.find('input').get(1).value).toBe('bah');
    expect(dom.find('textarea').get(1).value).toBe('11');
  });

  describe('adding new entries', function () {
    it('creates a new row in the table, but does not synchronize to model', function () {
      scope.model = {};
      let dom = this.compile('<map-object-editor model="model"></map-object-editor>')(scope);
      scope.$digest();
      dom.find('button').click();
      expect(dom.find('tbody tr').length).toBe(1);
      expect(dom.find('input').length).toBe(1);
      expect(dom.find('textarea').length).toBe(1);
    });

    it('does not flag multiple new rows without keys as having duplicate keys', function () {
      scope.model = {};
      let dom = this.compile('<map-object-editor model="model"></map-object-editor>')(scope);
      scope.$digest();
      dom.find('button').click();
      dom.find('button').click();

      expect(dom.find('tbody tr').length).toBe(2);
      expect(dom.find('input').length).toBe(2);
      expect(dom.find('textarea').length).toBe(2);

      expect(dom.find('.error-message').length).toBe(0);
    });
  });

  describe('removing entries', function () {
    it('removes the entry when the trash can is clicked', function () {
      scope.model = { foo: { bar: 'baz' } };
      let dom = this.compile('<map-object-editor model="model"></map-object-editor>')(scope);
      scope.$digest();

      expect(dom.find('input').length).toBe(1);
      expect(dom.find('textarea').length).toBe(1);

      dom.find('a').click();

      expect(dom.find('tbody tr').length).toBe(0);
      expect(dom.find('input').length).toBe(0);
      expect(dom.find('textarea').length).toBe(0);
      expect(scope.model.foo).toBeUndefined();
    });
  });

  describe('duplicate key handling', function () {
    it('provides a warning when a duplicate key is entered', function () {
      scope.model = { a: { bar: 'baz' }, b: '2' };
      let dom = this.compile('<map-object-editor model="model"></map-object-editor>')(scope);
      scope.$digest();

      $(dom.find('input')[1]).val('a').trigger('input');
      scope.$digest();

      expect(dom.find('.error-message').length).toBe(1);
    });
  });
});
