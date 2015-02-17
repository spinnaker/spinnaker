'use strict';

angular.module('deckApp.utils.waypoints.service', [
  'deckApp.utils.jQuery',
])
  .factory('waypointService', function($timeout, $) {

    var waypointRegistry = Object.create(null);

    function registerWaypointContainer(elementScope, element, key, offset) {
      waypointRegistry[key] = waypointRegistry[key] || Object.create(null);
      waypointRegistry[key].container = element;
      waypointRegistry[key].offset = offset;
      enableWaypointEvent(element, key);
      elementScope.$on('$destroy', function() {
        waypointRegistry[key] = null;
        disableWaypointEvent(key);
      });
    }

    function enableWaypointEvent(element, key) {
      var registryEntry = waypointRegistry[key];
      if (!registryEntry.scrollEnabled) {
        // because they do not affect rendering directly, we can debounce this pretty liberally
        element.bind('scroll.waypointEvents resize.waypointEvents', _.debounce(function() {
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

          waypointRegistry[key].lastWindow = _(inView).sortBy('top').pluck('elem').valueOf();

        }, 300));
        registryEntry.scrollEnabled = true;
      }
    }

    function disableWaypointEvent(key) {
      var registry = waypointRegistry[key];
      if (registry) {
        registry.container.unbind('scroll.waypointEvents resize.waypointEvents');
        registry.scrollEnabled = false;
      }
    }

    function restoreToWaypoint(key) {
      $timeout(function() {
        var registry = waypointRegistry[key];
        if (!registry) {
          return;
        }

        var candidates = registry.lastWindow || [],
          container = registry.container,
          containerScrollTop = container.scrollTop(),
          containerTop = container.offset().top;

        candidates.every(function(candidate) {
          var elem = $('[waypoint="' + candidate + '"]', container);
          if (elem.length) {
            container.scrollTop(containerScrollTop + elem.offset().top - containerTop - registry.offset);
            container.trigger('scroll');
            return false;
          }
          return true;

        });
      }, 50);
    }

    return {
      registerWaypointContainer: registerWaypointContainer,
      enableWaypointEvent: enableWaypointEvent,
      disableWaypointEvent: disableWaypointEvent,
      restoreToWaypoint: restoreToWaypoint,
    };

  });
