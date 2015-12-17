'use strict';

let angular = require('angular');

// Source: https://github.com/daniellmb/angular-uuid-service/blob/master/angular-uuid-service.js
module.exports = angular.module('spinnaker.core.utils.uuid.service', [])
  .factory('uuidService', function() {
    function getRandom(max) {
      return Math.random() * max;
    }

    function v4() {
      var id = '', i;

      for(i = 0; i < 36; i++)
      {
        if (i === 14) {
          id += '4';
        }
        else if (i === 19) {
          id += '89ab'.charAt(getRandom(4));
        }
        else if(i === 8 || i === 13 || i === 18 || i === 23) {
          id += '-';
        }
        else
        {
          id += '0123456789abcdef'.charAt(getRandom(16));
        }
      }
      return id;
    }

    return {
      generateUuid: v4,
    };
  });
