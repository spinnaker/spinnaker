'use strict';


angular.module('deckApp.utils.scrollTrigger', ['deckApp.utils.jQuery'])
  .factory('scrollTriggerService', function($window, $timeout, $) {
    var eventRegistry = Object.create(null),// creates {} with no prototype; ES6 Maps would be preferable (available in Chrome 38?),
        waypointRegistry = Object.create(null),
        registryCounter = 0,
        scrollEventActive = Object.create(null),
        $$window = $($window);

    /**
     * Registers a method to be called as soon as the element is in view.
     * It's generally gross to pass a scope to a service, but the code is so boilerplate around destroying the object
     * that I think it's better to handle here
     * @param elementScope the scope of the directive
     * @param element the element
     * @param targetId the value of the data-scroll-id attribute on a DOM node that should be watched for scroll
     *                 events. If null, the window's scroll event will be used. If not null, the value should be
     *                 unique within the DOM for that data attribute
     * @param method the method to be called, generally populating a collection. The method should be wrapped in $evalAsync
     *               if it's going to affect the scope - otherwise Angular won't notice it.
     */
    function register(elementScope, element, targetId, method) {
      var eventToRegister = { element: element, method: method };

      var scrollTarget = targetId ? '[data-scroll-id=' + targetId + ']' : 'window';

      if (eventIsInView(eventToRegister)) {
        method();
        return;
      }

      var id = registryCounter++;
      eventRegistry[scrollTarget] = eventRegistry[scrollTarget] || Object.create(null);
      eventRegistry[scrollTarget][id] = eventToRegister;
      elementScope.$on('$destroy', function() {
        deregister(scrollTarget, id);
      });
      activateScrollEvent(scrollTarget);
    }

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

    function deregister(scrollTarget, registeredEventId) {
      if (eventRegistry[scrollTarget] && eventRegistry[scrollTarget][registeredEventId]) {
        delete eventRegistry[scrollTarget][registeredEventId];
        if (!Object.keys(eventRegistry[scrollTarget]).length) {
          disableScrollEvent(scrollTarget);
        }
      }
    }

    function eventIsInView(registeredEvent, scrollBottom) {
      var elementRect = registeredEvent.element.get(0).getBoundingClientRect(),
          elementTop = elementRect.bottom - elementRect.height;
      if (elementTop < 0) {
        return false;
      }
      scrollBottom = scrollBottom || $$window.scrollTop() + $window.innerHeight;
      return registeredEvent && elementTop < scrollBottom;
    }

    var fireEvents = _.throttle(function fireEvents(scrollTarget) {
      var scrollBottom = $$window.scrollTop() + $window.innerHeight,
          executed = [];

      for (var registeredEventId in eventRegistry[scrollTarget]) {
        var registeredEvent = eventRegistry[scrollTarget][registeredEventId];
        if (eventIsInView(registeredEvent, scrollBottom)) {
          registeredEvent.method();
          executed.push(registeredEventId);
        }
      }
      executed.forEach(function(executed) { deregister(scrollTarget, executed); });
    }, 50);

    function activateScrollEvent(scrollTarget) {
      if (!scrollEventActive[scrollTarget]) {
        $(scrollTarget).bind('scroll.triggeredEvents resize.triggeredEvents', function() { fireEvents(scrollTarget); });
        scrollEventActive[scrollTarget] = true;
      }
    }

    function disableScrollEvent(scrollTarget) {
      if (scrollEventActive[scrollTarget]) {
        $(scrollTarget).unbind('scroll.triggeredEvents resize.triggeredEvents');
        scrollEventActive[scrollTarget] = false;
      }
    }

    return {
      register: register,
      registerWaypointContainer: registerWaypointContainer,
      enableWaypointEvent: enableWaypointEvent,
      disableWaypointEvent: disableWaypointEvent,
      restoreToWaypoint: restoreToWaypoint,
    };
  }
);
