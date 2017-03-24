'use strict';

let angular = require('angular');

import {NetflixSettings} from '../netflix.settings';

module.exports = angular.module('spinnaker.netflix.whatsNew.read.service', [])
  .factory('whatsNewReader', function ($http, $log) {
    function extractFileContent(data) {
      return data.files[NetflixSettings.whatsNew.fileName].content;
    }

    function getWhatsNewContents() {
      var gistId = NetflixSettings.whatsNew.gistId,
          accessToken = NetflixSettings.whatsNew.accessToken || null,
        url = ['https://api.github.com/gists/', gistId].join('');
      if (accessToken) {
        url += '?access_token=' + accessToken;
      }
      return $http.get(url)
        .then(
          function (result) {
            return {
              contents: extractFileContent(result.data),
              lastUpdated: result.data.updated_at,
            };
          },
          function(failure) {
            $log.warn('failed to retrieve gist for what\'s new dialog:', failure);
            return null;
          });
    }

    return {
      getWhatsNewContents: getWhatsNewContents,
    };
  });
