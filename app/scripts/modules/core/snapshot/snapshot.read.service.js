'use strict';

import {API_SERVICE} from '../api/api.service';

const angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.snapshot.read.service', [API_SERVICE])
  .factory('snapshotReader', function (API) {

    function getSnapshotHistory (application, account, params = {}) {
      return API.one('applications')
        .one(application)
        .one('snapshots')
        .one(account)
        .one('history')
        .withParams(params)
        .get();
    }

    return { getSnapshotHistory };
  });
