'use strict';

angular
  .module('spinnaker.subnet.read.service', ['restangular', 'spinnaker.caches.infrastructure'])
  .factory('subnetReader', function (Restangular, infrastructureCaches) {

    function listSubnets() {
      return Restangular.all('subnets')
        .withHttpConfig({cache: infrastructureCaches.subnets})
        .getList()
        .then(function(subnets) {
          return subnets.map(function(subnet) {
            subnet.label = subnet.purpose;
            subnet.deprecated = !!subnet.deprecated;
            if (subnet.deprecated) {
              subnet.label += ' (deprecated)';
            }
            return subnet;
          });
        });
    }

    return {
      listSubnets: listSubnets
    };

  });
