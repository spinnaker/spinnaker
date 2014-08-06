'use strict';

module.exports = function(momentService) {
  return function(input) {
    return momentService(input).calendar();
  };
};
