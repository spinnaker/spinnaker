'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.utils.dataConverter.service', [])
  .factory('dataConverterService', function () {

    function keyValueToEqualList(obj={}) {
      if (!obj) {
        return '';
      }
      return Object.keys(obj).map((key) => [key, obj[key]].join('=')).join('\n');
    }

    function equalListToKeyValue(list) {
      if (!list) {
        return {};
      }
      var keyValue = {};
      list.split(/\n/).forEach((line) => {
        let [key, val] = line.split('=');
        if (val !== undefined) {
          keyValue[key] = val;
        }
      });
      return keyValue;
    }

    return {
      keyValueToEqualList: keyValueToEqualList,
      equalListToKeyValue: equalListToKeyValue,
    };
  });
