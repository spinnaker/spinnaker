'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.api.provider', [
    require('../config/settings'),
    require('../authentication/authentication.initializer.service'),
  ])
  .factory('API', function ($http, $q, settings, authenticationInitializer) {
    const baseUrl = settings.gateUrl;

    // these config params will be sent on calls to backend
    const defaultParams = {
      timeout: settings.pollSchedule * 2 + 5000
    };

    var getData = (results) => {
      if (results.headers('content-type') && results.headers('content-type').indexOf('application/json') < 0) {
        authenticationInitializer.reauthenticateUser();
        return $q.reject(results);
      }
      return $q.when(results.data);
    };

    let baseReturn = function(config) {
      return {
        config: config,
        one: one(config),
        all: one(config),
        useCache: useCacheFn(config),
        withParams: withParamsFn(config),
        data: dataFn(config),
        get: getFn(config),
        getList: getFn(config),
        post: postFn(config),
        remove: removeFn(config),
        put: putFn(config),
      };
    };

    var useCacheFn = (config) => {
      return function(useCache = true) {
        config.cache = useCache;
        return baseReturn(config);
      };
    };

    var withParamsFn = (config) => {
      return function(params) {
        if(params) { config.params = params; }
        return baseReturn(config);
      };
    };

    // sets the data for PUT and POST methods
    var dataFn = (config) => {
      return function(data) {
        if(data) { config.data = data; }
        return baseReturn(config);
      };
    };

    // GET METHOD CALL
    let getFn = (config) => {
      return function(params) {
        config.method = 'get';
        angular.extend(config, defaultParams);
        if(params) { config.params = params; }
        return $http(config).then(getData);
      };
    };

    // POST METHOD CALL
    let postFn = (config) => {
      return function(data) {
        config.method = 'post';
        if(data) { config.data = data; }
        angular.extend(config, defaultParams);
        return $http(config).then(getData);
      };
    };

    // DELETE METHOD CALL
    let removeFn = (config) => {
      return function(params) {
        config.method = 'delete';
        if(params) { config.params = params; }
        angular.extend(config, defaultParams);
        return $http(config).then(getData);
      };
    };

    // PUT METHOD CALL
    let putFn = (config) => {
      return function(data) {
        config.method = 'put';
        if(data) { config.data = data; }
        angular.extend(config, defaultParams);
        return $http(config).then(getData);
      };
    };

    var one = function(config) {
      return function(...urls) {
        urls.forEach( function(url) {
          config.url = url ? `${config.url}/${url}` : config.url;
        });
        return baseReturn(config);
      };
    };

    var init = function(...urls) {
      let config = {url: baseUrl};
      urls.forEach( function(url) {
        config.url = `${config.url}/${url}`;
      });
      return baseReturn(config);
    };

    return {
      one: init,
      all: init,
      baseUrl: baseUrl
    };
  });
