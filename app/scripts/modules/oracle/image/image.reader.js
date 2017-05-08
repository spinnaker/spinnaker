'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.oraclebmcs.image.reader', [API_SERVICE])
  .factory('oraclebmcsImageReader', function ($q, API) {

    function findImages(params) {
      return API
        .one('images/find')
        .withParams(params)
        .get()
        .catch(function() { return []; });
    }

    function getImage(imageId, region, credentials) {
      return API
        .one('images')
        .one(credentials)
        .one(region)
        .one(imageId)
        .withParams({provider: 'oraclebmcs'})
        .get()
        .then(function(results) {
          return results && results.length ? results[0] : null;
        },
        function() {
          return null;
        });
    }

    return {
      findImages,
      getImage,
    };
  });
