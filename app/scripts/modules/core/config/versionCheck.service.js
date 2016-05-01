'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.config.versionCheckService', [
    require('./settings'),
    require('../widgets/notifier/notifier.service.js'),
  ])
  .factory('versionCheckService', function ($http, $timeout, notifierService, $log, $filter) {

    let currentVersion = require('../../../../../version.json');

    $log.debug('Deck version', currentVersion.version, 'created', $filter('timestamp')(currentVersion.created));

    let checkVersion = () => {
      let url = '/version.json?_=' + new Date().getTime();
      $timeout(() => $http.get(url).then(versionRetrieved, checkVersion), 15000);
    };

    let versionRetrieved = (response) => {
      let data = response.data;
      if (data.version === currentVersion.version) {
        checkVersion();
      } else {
        $log.debug('New Deck version:', data.version, 'created', $filter('timestamp')(data.created));
        notifierService.publish(
          `A new version of Spinnaker is available
            <a role="button" class="action" onclick="document.location.reload(true)">Refresh</button>`);
      }
    };

    return {
      initialize: checkVersion
    };
  })
  .run(function (versionCheckService, settings) {
    if (settings.checkForUpdates) {
      versionCheckService.initialize();
    }
  });
