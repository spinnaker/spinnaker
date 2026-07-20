'use strict';

describe('Component: applicationLinks', function () {
  const applicationLinksModule = require('./applicationLinks.component').name;

  beforeEach(window.module(applicationLinksModule));

  it('declares ui.sortable so instance links can be reordered', function () {
    expect(window.angular.module(applicationLinksModule).requires).toContain('ui.sortable');
  });

  it(
    'loads the ui-sortable directive module',
    window.inject(function ($injector) {
      expect($injector.has('uiSortableDirective')).toBe(true);
    }),
  );
});
