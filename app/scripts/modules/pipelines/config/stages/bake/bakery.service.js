'use strict';

angular.module('deckApp.pipelines.stage.bake')
  .factory('bakeryService', function($q) {

    function getRegions() {
      return $q.when(['us-east-1', 'us-west-1', 'us-west-2', 'eu-west-1']);
    }

    function getBaseOsOptions() {
      return $q.when(['ubuntu', 'centos', 'trusty']);
    }

    function getBaseLabelOptions() {
      return $q.when(['release', 'candidate']);
    }

    return {
      getRegions: getRegions,
      getBaseOsOptions: getBaseOsOptions,
      getBaseLabelOptions: getBaseLabelOptions
    };
  });
