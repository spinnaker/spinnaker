'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.ajaxError.interceptor', [
    require('../utils/lodash.js')
  ])
  .factory('ajaxErrorInterceptor', function($q, $analytics, _) {

    return {
      'requestError': function(rejection) {
        var config = rejection ? rejection.config : {};

        _.defaults(config, {
          url: 'Unknown Url',
          method: 'Unknown Method'
        });

        var action = config.url;
        var label = config.method;

        $analytics.eventTrack(action, {category: 'Ajax Request Error', label: label, noninteraction: true});

        return $q.reject(rejection);
      },

      'responseError': function(rejection) {
        var config = rejection ? rejection.config : {};

        _.defaults(config, {
          url: 'Unknown Url',
          method: 'Unknown Method'
        });

        var action = config.url;
        var label = config.method;

        $analytics.eventTrack(action, {category: 'Ajax Response Error', label: label, noninteraction: true});

        return $q.reject(rejection);
      }
    };
  });

