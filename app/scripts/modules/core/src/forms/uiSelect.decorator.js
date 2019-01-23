'use strict';

module.exports = function($provide) {
  $provide.decorator('uiSelectMultipleDirective', function($delegate) {
    const [uiSelect] = $delegate,
      originalLink = uiSelect.link,
      SELECT_EVENT_KEY = 'uis:select';

    uiSelect.link = function(scope, element, attrs, ctrls) {
      originalLink.apply(this, arguments);
      const [$select] = ctrls;
      scope.$$listeners[SELECT_EVENT_KEY] = [];
      scope.$on(SELECT_EVENT_KEY, function(event, item) {
        if ($select.selected.length >= $select.limit) {
          return;
        }
        if (!event.defaultPrevented) {
          $select.selected.push(item);
          scope.$selectMultiple.updateModel();
        }
      });
    };

    return $delegate;
  });

  // The GCP nested approach throws an exception when activating the select when there's a nested select option,
  // but it's harmless so we trap it and discard it

  $provide.decorator('uiSelectMinErr', function($delegate) {
    return function handledError() {
      var original = $delegate;
      if (arguments.length === 3) {
        if (
          arguments[0] === 'choices' &&
          arguments[1] === `Expected multiple .ui-select-choices-row but got '{0}'.` &&
          arguments[2] === 0
        ) {
          throw new Error('IGNORE', 'IGNORE');
        }
      }
      return original.apply(this, arguments);
    };
  });

  $provide.decorator('$exceptionHandler', function($delegate) {
    return function(exception, cause) {
      if (exception && exception.message === 'IGNORE') {
        return;
      }

      if (Array.isArray(exception) && exception.length && exception[0] instanceof Error) {
        exception.forEach(e => $delegate(e, cause));
      } else {
        $delegate(exception, cause);
      }
    };
  });
};
