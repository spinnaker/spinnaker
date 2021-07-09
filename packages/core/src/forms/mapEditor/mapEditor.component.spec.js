'use strict';

describe('Component: mapEditor', function () {
  var scope;

  beforeEach(window.module(require('./mapEditor.component').name));

  // https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$compile
  beforeEach(
    window.module(($compileProvider) => {
      $compileProvider.preAssignBindingsEnabled(true);
    }),
  );

  beforeEach(
    window.inject(function ($rootScope, $compile) {
      scope = $rootScope.$new();
      this.compile = $compile;
    }),
  );

  it('initializes with provided values', function () {
    scope.model = { foo: 'bar', bah: 11 };
    let dom = this.compile('<map-editor model="model"></map-editor>')(scope);
    scope.$digest();

    expect(dom.find('input').length).toBe(4);
    expect(dom.find('input').get(0).value).toBe('foo');
    expect(dom.find('input').get(1).value).toBe('bar');
    expect(dom.find('input').get(2).value).toBe('bah');
    expect(dom.find('input').get(3).value).toBe('11');
  });

  describe('empty value handling', function () {
    it('ignores empty values when synchronizing to the model', function () {
      scope.model = { foo: 'bar', bah: 11 };
      let dom = this.compile('<map-editor model="model"></map-editor>')(scope);
      scope.$digest();
      $(dom.find('input')[3]).val('').change();
      scope.$digest();
      expect(scope.model.foo).toBe('bar');
      expect(scope.model.bah).toBeUndefined();
    });

    it('writes empty values when allowEmpty flag is set', function () {
      scope.model = { foo: 'bar', bah: 11 };
      let dom = this.compile('<map-editor model="model" allow-empty="true"></map-editor>')(scope);
      scope.$digest();
      $(dom.find('input')[3]).val('').change();
      scope.$digest();
      expect(scope.model.foo).toBe('bar');
      expect(scope.model.bah).toBe('');
    });
  });

  describe('overriding table headings', function () {
    it('defaults to "Key" and "Value"', function () {
      scope.model = {};
      let dom = this.compile('<map-editor model="model"></map-editor>')(scope);
      scope.$digest();
      expect(dom.find('th')[0].innerText).toBe('Key');
      expect(dom.find('th')[1].innerText).toBe('Value');
    });

    it('can override "Key" and "Value"', function () {
      scope.model = {};
      let dom = this.compile('<map-editor model="model" key-label="some key" value-label="the value"></map-editor>')(
        scope,
      );
      scope.$digest();
      expect(dom.find('th')[0].innerText).toBe('some key');
      expect(dom.find('th')[1].innerText).toBe('the value');
    });
  });

  describe('adding new entries', function () {
    it('creates a new row in the table, but does not synchronize to model', function () {
      scope.model = {};
      let dom = this.compile('<map-editor model="model"></map-editor>')(scope);
      scope.$digest();
      dom.find('button').click();
      expect(dom.find('tbody tr').length).toBe(1);
      expect(dom.find('input').length).toBe(2);
    });

    it('does not flag multiple new rows without keys as having duplicate keys', function () {
      scope.model = {};
      let dom = this.compile('<map-editor model="model"></map-editor>')(scope);
      scope.$digest();
      dom.find('button').click();
      dom.find('button').click();
      expect(dom.find('tbody tr').length).toBe(2);
      expect(dom.find('input').length).toBe(4);
      expect(dom.find('.error-message').length).toBe(0);
    });

    it('if provided, calls the onChange event when key/value are both entered', function () {
      let changeDetected = false;
      scope.onChange = () => (changeDetected = true);
      scope.model = { foo: 'bar' };
      let dom = this.compile('<map-editor model="model" on-change="onChange()"></map-editor>')(scope);
      scope.$digest();
      expect(dom.find('input').length).toBe(2);
      dom.find('button').click();
      scope.$digest();
      expect(dom.find('input').length).toBe(4);
      expect(changeDetected).toBe(false);

      $(dom.find('input')[2]).val('bah').change();
      scope.$digest();
      expect(changeDetected).toBe(false);
      $(dom.find('input')[3]).val('aah').change();
      scope.$digest();
      expect(changeDetected).toBe(true);
    });
  });

  describe('removing entries', function () {
    it('removes the entry when the trash can is clicked', function () {
      scope.model = { foo: '1' };
      let dom = this.compile('<map-editor model="model"></map-editor>')(scope);
      scope.$digest();
      expect(dom.find('input').length).toBe(2);
      dom.find('a').click();
      expect(dom.find('tbody tr').length).toBe(0);
      expect(dom.find('input').length).toBe(0);
      expect(scope.model.foo).toBeUndefined();
    });

    it('calls the onChange event if provided', function () {
      let changeDetected = false;
      scope.onChange = () => (changeDetected = true);
      scope.model = { foo: '1' };
      let dom = this.compile('<map-editor model="model" on-change="onChange()"></map-editor>')(scope);
      scope.$digest();
      dom.find('a').click();
      expect(dom.find('tbody tr').length).toBe(0);
      expect(dom.find('input').length).toBe(0);
      expect(scope.model.foo).toBeUndefined();
      expect(changeDetected).toBe(true);
    });
  });

  describe('duplicate key handling', function () {
    it('provides a warning when a duplicate key is entered', function () {
      scope.model = { a: '1', b: '2' };
      let dom = this.compile('<map-editor model="model"></map-editor>')(scope);
      scope.$digest();
      $(dom.find('input')[2]).val('a').trigger('input');
      scope.$digest();
      expect(dom.find('.error-message').length).toBe(1);
    });

    it('removes the warning when the duplicate key is removed', function () {
      scope.model = { a: '1', b: '2' };
      let dom = this.compile('<map-editor model="model"></map-editor>')(scope);
      scope.$digest();
      $(dom.find('input')[2]).val('a').trigger('input');
      scope.$digest();
      expect(dom.find('input').length).toBe(4);
      expect(dom.find('.error-message').length).toBe(1);
      $(dom.find('a')[1]).click();
      scope.$digest();
      expect(dom.find('.error-message').length).toBe(0);
      expect(dom.find('input').length).toBe(2);
    });

    it('only flags the changed entry, regardless of order relative to duplicated key', function () {
      scope.model = { a: '1', b: '2' };
      let dom = this.compile('<map-editor model="model"></map-editor>')(scope);
      scope.$digest();
      $(dom.find('input')[0]).val('b').trigger('input');
      scope.$digest();
      expect(dom.find('tbody tr:first-child .error-message').length).toBe(1);
      expect(dom.find('tbody tr:nth-child(2) .error-message').length).toBe(0);

      $(dom.find('input')[2]).val('a').trigger('input');
      scope.$digest();
      expect(dom.find('tbody tr:first-child .error-message').length).toBe(0);
      expect(dom.find('tbody tr:nth-child(2) .error-message').length).toBe(0);

      $(dom.find('input')[2]).val('b').trigger('input');
      scope.$digest();
      expect(dom.find('tbody tr:first-child .error-message').length).toBe(0);
      expect(dom.find('tbody tr:nth-child(2) .error-message').length).toBe(1);
    });
  });

  describe('hidden keys', function () {
    it('does not render key if included in `hiddenKeys`', function () {
      scope.model = { a: '1', b: '2' };
      scope.hiddenKeys = ['a'];
      const dom = this.compile('<map-editor model="model" hidden-keys="hiddenKeys"></map-editor>')(scope);
      scope.$digest();
      expect($(dom.find('tbody tr')).length).toBe(1);
      expect(dom.find('input').get(0).value).toBe('b');
      expect(dom.find('input').get(1).value).toBe('2');
    });
  });
});
