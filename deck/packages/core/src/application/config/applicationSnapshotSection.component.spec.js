'use strict';

describe('Component: applicationSnapshotSection', function () {
  let $componentController;

  beforeEach(window.module(require('./applicationSnapshotSection.component').name));

  beforeEach(
    window.inject(function (_$componentController_) {
      $componentController = _$componentController_;
    }),
  );

  it('does not read the application binding before initialization', function () {
    expect(function () {
      $componentController('applicationSnapshotSection', { $scope: null });
    }).not.toThrow();
  });

  it('initializes the snapshot action after the application binding is available', function () {
    const controller = $componentController(
      'applicationSnapshotSection',
      { $scope: null },
      {
        application: {
          attributes: {},
          hasError: false,
          name: 'fnord',
          notFound: false,
        },
      },
    );

    controller.$onInit();

    expect(controller.takeSnapshot).toEqual(jasmine.any(Function));
  });
});
