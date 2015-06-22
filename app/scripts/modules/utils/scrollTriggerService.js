'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.utils.scrollTrigger', [
  require('./jQuery.js'),
  require('./lodash.js'),
])
  .factory('scrollTriggerService', function($window, $, _) {
    var eventRegistry = Object.create(null),// creates {} with no prototype; ES6 Maps would be preferable (available in Chrome 38?),
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

    function deregister(scrollTarget, registeredEventId) {
      if (eventRegistry[scrollTarget] && eventRegistry[scrollTarget][registeredEventId]) {
        delete eventRegistry[scrollTarget][registeredEventId];
        if (!Object.keys(eventRegistry[scrollTarget]).length) {
          disableScrollEvent(scrollTarget);
        }
      }
    }

    function eventIsInView(registeredEvent, scrollBottom) {
      var rect = registeredEvent.element.get(0).getBoundingClientRect();
      var elementTop = rect.top;
      if (rect.bottom < 0) {
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
    };
  }
);
