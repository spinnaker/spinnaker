'use strict';

/*jshint camelcase: false */
angular.module('deckApp.whatsNew.read.service', [
  'deckApp.settings',
])
  .factory('whatsNewReader', function($http, settings) {
    function extractFileContent(data) {
      return data.files[settings.whatsNew.fileName].content;
    }

    function getWhatsNewContents() {
      var token = settings.whatsNew.accessToken,
          gistId = settings.whatsNew.gistId,
          url = ['https://api.github.com/gists/', gistId, '?access_token=', token].join('');
      return $http.get(url)
        .then(function (result) {
          return {
            contents: extractFileContent(result.data),
            lastUpdated: result.data.updated_at,
          };
        });
    }

    return {
      getWhatsNewContents: getWhatsNewContents,
    };
  });
