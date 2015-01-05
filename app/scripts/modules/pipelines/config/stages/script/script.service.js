'use strict';

angular.module('deckApp.pipelines.stage.script')
  .factory('scriptService', function($q) {

    function getCredentials() {
      return $q.when(['spinnaker']);
    }

    return {
      getCredentials: getCredentials,
    };
  });
