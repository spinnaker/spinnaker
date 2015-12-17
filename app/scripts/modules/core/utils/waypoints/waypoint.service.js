'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.utils.waypoints.service', [
  require('../jQuery.js'),
  require('../lodash.js'),
])
  .factory('waypointService', function($timeout, $, _, $log) {

    var waypointRegistry = Object.create(null);

    function registerWaypointContainer(elementScope, element, key, offset) {
      waypointRegistry[key] = waypointRegistry[key] || Object.create(null);
      waypointRegistry[key].container = element;
      waypointRegistry[key].offset = offset;
      enableWaypointEvent(element, key);
      elementScope.$on('$destroy', function() {
        disableWaypointEvent(key);
      });
    }

    function enableWaypointEvent(element, key) {
      var registryEntry = waypointRegistry[key];
      if (!registryEntry.scrollEnabled) {
        // because they do not affect rendering directly, we can debounce this pretty liberally
        // but delay in case the scroll triggers a render of other elements and the top changes
        element.bind('scroll.waypointEvents resize.waypointEvents', _.debounce(function() {
          $timeout(function() {
            var containerRect = element.get(0).getBoundingClientRect(),
              topThreshold = containerRect.top + registryEntry.offset,
              waypoints = element.find('[waypoint]'),
              inView = [];
            waypoints.each(function(idx, waypoint) {
              var waypointRect = waypoint.getBoundingClientRect();
              if (waypointRect.bottom >= topThreshold && waypointRect.top <= containerRect.bottom) {
                  inView.push({ top: waypointRect.top, elem: waypoint.getAttribute('waypoint') });
              }
            });
            waypointRegistry[key].lastWindow = _.sortBy(inView, 'top');
          });
        }, 300));
        registryEntry.scrollEnabled = true;
      }
    }

    function disableWaypointEvent(key) {
      var registry = waypointRegistry[key];
      if (registry) {
        registry.container.unbind('scroll.waypointEvents resize.waypointEvents');
        registry.scrollEnabled = false;
        registry.container = null;
      }
    }

    function restoreToWaypoint(key) {
      $timeout(function() {
        var registry = waypointRegistry[key];
        if (!registry || !registry.container) {
          $log.info('no waypoint found for ' + key + ', returning');
          return;
        }

        var candidates = registry.lastWindow || [],
          container = registry.container,
          containerScrollTop = container.scrollTop();

        candidates.every(function(candidate) {
          var elem = $('[waypoint="' + candidate.elem + '"]', container);
          if (elem.length) {
            container.scrollTop(containerScrollTop + elem.offset().top - candidate.top);
            container.trigger('scroll');
            return false;
          }
          return true;

        });
      }, 50);
    }

    function getLastWindow(key) {
      return waypointRegistry[key] ? _.pluck(waypointRegistry[key].lastWindow, 'elem') : [];
    }

    return {
      registerWaypointContainer: registerWaypointContainer,
      enableWaypointEvent: enableWaypointEvent,
      disableWaypointEvent: disableWaypointEvent,
      restoreToWaypoint: restoreToWaypoint,
      getLastWindow: getLastWindow,
    };

  });
