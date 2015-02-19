'use strict';

angular
  .module('deckApp.elasticIp.read.service', [
    'restangular',
  ])
  .factory('elasticIpReader', function (Restangular, $exceptionHandler) {
    function getElasticIpsForCluster(application, account, clusterName, region) {
      return Restangular.one('applications', application).all('clusters').all(account).all(clusterName).one('elasticIps').all(region)
        .getList()
        .then(function (elasticIps) {
          return elasticIps;
        }, function(error) {
          $exceptionHandler(error, 'error retrieving elastic ips');
          return [];
        });
    }

    return {
      getElasticIpsForCluster: getElasticIpsForCluster
    };
  });
