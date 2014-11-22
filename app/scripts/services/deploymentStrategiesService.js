'use strict';


angular.module('deckApp')
  .factory('deploymentStrategiesService', function ($q) {

    var strategies = [
      {label: 'Highlander', key: 'highlander', description: 'Destroys previous server group as soon as new server group passes health checks'},
      {label: 'Red/Black', key: 'redblack', description: 'Disables previous server group as soon as new server group passes health checks'},
      {label: 'None', key: '', description: 'Creates the next server group with no impact on existing server groups'},
    ];

    function listAvailableStrategies() {
      return $q.when(strategies);
    }

    return {
      listAvailableStrategies: listAvailableStrategies
    };

  });
