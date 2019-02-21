'use strict';

const angular = require('angular');

import _ from 'lodash';

module.exports = angular
  .module('spinnaker.oracle.serverGroup.transformer', [])
  .factory('oracleServerGroupTransformer', [
    '$q',
    function($q) {
      let PROVIDER = 'oracle';

      function normalizeServerGroup(serverGroup) {
        return $q.when(serverGroup);
      }

      function convertServerGroupCommandToDeployConfiguration(base) {
        let command = _.defaults({ backingData: [], viewState: [] }, base);
        command.cloudProvider = PROVIDER;
        return command;
      }

      return {
        convertServerGroupCommandToDeployConfiguration,
        normalizeServerGroup,
      };
    },
  ]);
