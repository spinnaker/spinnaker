'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.config.versionCheckService', [
    require('./settings'),
    require('../widgets/notifier/notifier.service.js'),
  ])
  .factory('versionCheckService', function ($http, $timeout, notifierService, $log, $filter) {

    let currentVersion = require('../../../../../version.json');
    let newVersionSeenCount = 0;

    $log.debug('Deck version', currentVersion.version, 'created', $filter('timestamp')(currentVersion.created));

    let checkVersion = () => {
      let url = '/version.json?_=' + new Date().getTime();
      $timeout(() => $http.get(url).then(versionRetrieved, checkVersion), 30000);
    };

    let versionRetrieved = (response) => {
      let data = response.data;
      if (data.version === currentVersion.version) {
        newVersionSeenCount = 0;
        checkVersion();
      } else {
        newVersionSeenCount++;
        if (newVersionSeenCount < 6) {
          checkVersion();
        } else {
          $log.debug('New Deck version:', data.version, 'created', $filter('timestamp')(data.created));
          notifierService.publish(
            `A new version of Spinnaker is available
              <a role="button" class="action" onclick="document.location.reload(true)">Refresh</a>`);
        }
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
