'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.keyPairs.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
  ])
  .factory('keyPairsReader', function ($q, Restangular) {

    function listKeyPairs() {
      return Restangular.all('keyPairs')
        .withHttpConfig({cache: true})
        .getList();
    }

    return {
      listKeyPairs: listKeyPairs
    };

  });
