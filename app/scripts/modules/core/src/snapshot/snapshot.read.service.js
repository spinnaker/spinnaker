'use strict';

import { API } from '../api/ApiService';

const angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.snapshot.read.service', []).factory('snapshotReader', function() {
  function getSnapshotHistory(application, account, params = {}) {
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
