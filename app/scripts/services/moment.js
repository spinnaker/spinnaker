'use strict';

angular.module('deckApp')
  .factory('momentService', function($window) {
    var moment = $window.moment;

    function toCalendar(timestamp) {
      return moment(timestamp).calendar();
    }
    return {
      calendar: toCalendar
    };
  });
