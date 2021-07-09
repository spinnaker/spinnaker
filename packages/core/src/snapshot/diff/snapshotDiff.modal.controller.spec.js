'use strict';

import { JsonUtils } from '../../utils';

describe('Controller: SnapshotDiffModalCtrl', function () {
  beforeEach(window.module(require('./snapshotDiff.modal.controller').name));

  beforeEach(
    window.inject(function ($controller, $filter) {
      this.controller = $controller('SnapshotDiffModalCtrl', {
        availableAccounts: ['my-google-account'],
        application: { name: 'myApplication' },
        $uibModalInstance: { dismiss: angular.noop },
        $filter,
      });
    }),
  );

  it('should instantiate the controller', function () {
    expect(this.controller).toBeDefined();
  });

  describe('updateDiff', function () {
    beforeEach(function () {
      this.controller.snapshots = [
        {
          contents: 'third snapshot',
        },
        {
          contents: 'second snapshot',
        },
        {
          contents: 'first snapshot',
        },
      ];

      spyOn(JsonUtils, 'diff');
      // prevents unnecessary call to JsonUtilityService#diff).
      spyOn(this.controller, 'getSnapshotHistoryForAccount');
    });

    it(`when compareTo === 'most recent', it should compare each snapshot version
        to the most recent snapshot`, function () {
      this.controller.compareTo = 'most recent';
      [0, 1, 2].forEach((version) => {
        this.controller.version = version;
        this.controller.updateDiff();
        expect(JsonUtils.diff).toHaveBeenCalledWith(
          'third snapshot',
          ['third snapshot', 'second snapshot', 'first snapshot'][version],
        );
      });
    });

    it(`when compareTo === 'previous', it should compare each snapshot version
        to the previous snapshot (if previous exists)`, function () {
      this.controller.version = 0;
      this.controller.updateDiff();
      expect(JsonUtils.diff).toHaveBeenCalledWith('second snapshot', 'third snapshot');

      this.controller.version = 1;
      this.controller.updateDiff();
      expect(JsonUtils.diff).toHaveBeenCalledWith('first snapshot', 'second snapshot');

      this.controller.version = 2;
      this.controller.updateDiff();

      expect(JsonUtils.diff).toHaveBeenCalledWith('first snapshot', 'first snapshot');
    });
  });
});
