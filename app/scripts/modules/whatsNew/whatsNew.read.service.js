'use strict';

/*jshint camelcase: false */
angular.module('deckApp.whatsNew.read.service', [
  'deckApp.settings',
])
  .factory('whatsNewReader', function ($http, settings, $log) {
    function extractFileContent(data) {
      return data.files[settings.whatsNew.fileName].content;
    }

    function getWhatsNewContents() {
      var token = settings.whatsNew.accessToken,
        gistId = settings.whatsNew.gistId,
        url = ['https://api.github.com/gists/', gistId, '?access_token=', token].join('');
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
