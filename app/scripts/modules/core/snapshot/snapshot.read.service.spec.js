'use strict';

import {SETTINGS} from 'core/config/settings';

describe('Service: SnapshotRead', function() {
  const application = 'snapshotReadTest';
  const account = 'my-google-account';
  const params = { limit: 20 };

  beforeEach(
    window.module(require('./snapshot.read.service.js')));

  beforeEach(window.inject(function($httpBackend, snapshotReader) {
    this.$httpBackend = $httpBackend;
    this.snapshotReader = snapshotReader;
  }));

  describe('getSnapshotHistory', function () {
    it('makes request to correct gate endpoint', function () {
      this.$httpBackend
        .expectGET(`${SETTINGS.gateUrl}/applications/snapshotReadTest/snapshots/my-google-account/history?limit=20`)
        .respond([]);
      this.snapshotReader.getSnapshotHistory(application, account, params);

      this.$httpBackend.flush();
    });

    it('returns what the endpoint returns', function () {
      this.$httpBackend
        .whenGET(`${SETTINGS.gateUrl}/applications/snapshotReadTest/snapshots/my-google-account/history?limit=20`)
        .respond([{ infrastructure: 'myInfrastructure' }]);

      this.snapshotReader.getSnapshotHistory(application, account, params)
        .then((snapshots) => {
          expect(snapshots).toEqual([{ infrastructure: 'myInfrastructure' }]);
        })
        .catch((error) => {
          expect(error).toBeUndefined();
        });

      this.$httpBackend.flush();
    });

    it('does not fail if not passed parameters', function () {
      this.$httpBackend
        .expectGET(`${SETTINGS.gateUrl}/applications/snapshotReadTest/snapshots/my-google-account/history`)
        .respond([]);
      try {
        this.snapshotReader.getSnapshotHistory(application, account)
          .catch((e) => {
            expect(e).toBeUndefined();
          });
      } catch (e) {
        expect(e).toBeUndefined();
      }

      this.$httpBackend.flush();
    });
  });
});
