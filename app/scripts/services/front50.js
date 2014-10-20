'use strict';


angular.module('deckApp')
  .factory('front50', function(settings, $http) {
    return {
      createApplication: function(app) {
        return $http.post(settings.front50Url+'/applications/name/'+app.name, app);
      },
      deleteApplication: function(app) {
        return $http.delete(settings.front50Url+'/applications/name/'+app.name);
      },
    };

  });
