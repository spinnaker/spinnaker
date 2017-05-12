'use strict';

let angular = require('angular');

import _ from 'lodash';

module.exports = angular.module('spinnaker.oraclebmcs.serverGroup.transformer', [])
  .factory('oraclebmcsServerGroupTransformer', function ($q) {

    let PROVIDER = 'oraclebmcs';

    function normalizeServerGroup(serverGroup) {
      return $q.when(serverGroup);
    }

    function convertServerGroupCommandToDeployConfiguration(base) {
      let command = _.defaults({backingData: [], viewState: []}, base);
      command.cloudProvider = PROVIDER;
      return command;
    }

    return {
      convertServerGroupCommandToDeployConfiguration,
      normalizeServerGroup,
    };
  });
