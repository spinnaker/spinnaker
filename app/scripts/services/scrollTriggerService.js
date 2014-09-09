'use strict';


angular.module('deckApp')
  .factory('scrollTriggerService', function($window, $) {
    var eventRegistry = Object.create(null), // creates {} with no prototype; ES6 Maps would be preferable (available in Chrome 38?)
        registryCounter = 0,
        scrollEventActive,
        $$window = $($window);

    /**
     * Registers a method to be called as soon as the element is in view.
     * It's generally gross to pass a scope to a service, but the code is so boilerplate around destroying the object
     * that I think it's better to handle here
     * @param elementScope the scope of the directive
     * @param element the element
     * @param method the method to be called, generally populating a collection. The method should be wrapped in $evalAsync
     *               if it's going to affect the scope - otherwise Angular won't notice it.
     */
    function register(elementScope, element, method) {
      var eventToRegister = { element: $(element), method: method };

      if (eventIsInView(eventToRegister)) {
        method();
        return;
      }

      var id = registryCounter++;
      eventRegistry[id] = eventToRegister;
      elementScope.$on('$destroy', function() {
        deregister(id);
      });
      activateScrollEvent();
    }

    function deregister(registeredEventId) {
      if (eventRegistry[registeredEventId]) {
        delete eventRegistry[registeredEventId];
        if (!Object.keys(eventRegistry).length) {
          disableScrollEvent();
        }
      }
    }

    function eventIsInView(registeredEvent, scrollBottom) {
      var elementTop = registeredEvent.element.offset().top;
      if (elementTop < 0) {
        return false;
      }
      scrollBottom = scrollBottom || $$window.scrollTop() + $window.innerHeight;
      return registeredEvent && elementTop < scrollBottom;
    }

    function fireEvents() {
      var scrollBottom = $$window.scrollTop() + $window.innerHeight,
          executed = [];

      for (var registeredEventId in eventRegistry) {
        var registeredEvent = eventRegistry[registeredEventId];
        if (eventIsInView(registeredEvent, scrollBottom)) {
          registeredEvent.method();
          executed.push(registeredEventId);
        }
      }
      executed.forEach(deregister);
    }

    function activateScrollEvent() {
      if (!scrollEventActive) {
        $$window.bind('scroll.triggeredEvents', fireEvents);
        scrollEventActive = true;
      }
    }

    function disableScrollEvent() {
      if (scrollEventActive) {
        $$window.unbind('scroll.triggeredEvents');
        scrollEventActive = false;
      }
    }

    return {
      register: register
    };
  }
);
