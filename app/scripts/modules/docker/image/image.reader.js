'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.docker.image.reader', [])
    .factory('dockerImageReader', function ($q, Restangular) {
        function findImages(params) {
            return Restangular.all('images/find').getList(params, {}).then(function(results) {
                    return results;
                },
                function() {
                    return [];
                });
        }

        return {
            findImages: findImages,
        };
    });
