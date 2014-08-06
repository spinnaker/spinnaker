'use strict';

module.exports = function(dateFromTimestampFilter) {
  return function(input) {
    input.started = dateFromTimestampFilter(input.startTime);
    if (input.endtime) {
      input.ended = dateFromTimestampFilter(input.endTime);
    }
    return input;
  };

};
