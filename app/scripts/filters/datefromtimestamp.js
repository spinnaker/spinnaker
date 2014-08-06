'use strict';

module.exports = function() {
  return function(input) {
    var date = new Date(0);
    date.setUTCMilliseconds(parseInt(input));
    return date;
  };
};
