'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.bake.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
])
  .factory('bakeryService', function($q, _, Restangular) {

    function getRegions(provider) {
      if (!provider || provider === 'aws') {
        return $q.when(['us-east-1', 'us-west-1', 'us-west-2', 'eu-west-1']);
      }
      if (provider === 'gce') {
        return $q.when(['asia-east1', 'us-central1', 'europe-west1']);
      }
    }

    function getBaseOsOptions(provider) {
      if (provider) {
        return getBaseOsOptions().then(function(options) {
          return _.find(options, { cloudProvider: provider });
        });
      }
      return Restangular
        .all('bakery/options')
        .withHttpConfig({cache: true})
        .getList();
    }

    function getBaseLabelOptions() {
      return $q.when(['release', 'candidate', 'previous', 'unstable']);
    }

    function getVmTypes() {
      return $q.when(['pv', 'hvm']);
    }

    function getStoreTypes() {
      return $q.when(['ebs', 's3', 'docker']);
    }

    return {
      getRegions: getRegions,
      getBaseOsOptions: getBaseOsOptions,
      getVmTypes: getVmTypes,
      getBaseLabelOptions: getBaseLabelOptions,
      getStoreTypes: getStoreTypes,
    };
  });
