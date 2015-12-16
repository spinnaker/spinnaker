'use strict';

let angular = require('angular');

/**
 * Lifted literally 100% from Angular internals
 */
module.exports = angular.module('spinnaker.core.navigation.urlParser.service', [])
  .factory('urlParser', function () {
    /**
     * Tries to decode the URI component without throwing an exception.
     *
     * @private
     * @param str value potential URI component to check.
     * @returns {boolean} True if `value` can be decoded
     * with the decodeURIComponent function.
     */
    function tryDecodeURIComponent(value) {
      try {
        return decodeURIComponent(value);
      } catch (e) {
        // Ignore any invalid uri component
      }
    }


    /**
     * Parses an escaped url query string into key-value pairs.
     * @returns {Object.<string,boolean|Array>}
     */
    function parseKeyValue(/**string*/keyValue) {
      var obj = {};
      angular.forEach((keyValue || '').split('&'), function(keyValue) {
        var splitPoint, key, val;
        if (keyValue) {
          key = keyValue = keyValue.replace(/\+/g, '%20');
          splitPoint = keyValue.indexOf('=');
          if (splitPoint !== -1) {
            key = keyValue.substring(0, splitPoint);
            val = keyValue.substring(splitPoint + 1);
          }
          key = tryDecodeURIComponent(key);
          if (angular.isDefined(key)) {
            val = angular.isDefined(val) ? tryDecodeURIComponent(val) : true;
            if (!hasOwnProperty.call(obj, key)) {
              obj[key] = val;
            } else if (angular.isArray(obj[key])) {
              obj[key].push(val);
            } else {
              obj[key] = [obj[key], val];
            }
          }
        }
      });
      return obj;
    }

    return {
      parseQueryString: parseKeyValue,
    };

  });
