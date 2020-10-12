import { IScope } from 'angular';
import { bootstrapModule } from './bootstrap.module';

uiSelectDecorator.$inject = ['$provide'];
function uiSelectDecorator($provide: ng.auto.IProvideService) {
  $provide.decorator('uiSelectMultipleDirective', [
    '$delegate',
    '$timeout',
    function ($delegate: any, $timeout: ng.ITimeoutService) {
      const [uiSelect] = $delegate;
      const originalLink = uiSelect.link;
      const SELECT_EVENT_KEY = 'uis:select';

      uiSelect.link = function (scope: IScope, _element: any, _attrs: any, ctrls: any) {
        originalLink.apply(this, arguments);
        const [$select] = ctrls;
        scope.$$listeners[SELECT_EVENT_KEY] = [];
        scope.$on(SELECT_EVENT_KEY, function (event, item) {
          if ($select.selected.length >= $select.limit) {
            return;
          }
          if (!event.defaultPrevented) {
            $select.selected.push(item);
            const locals = {
              [$select.parserResult.itemName]: item,
            };
            $timeout(function () {
              if ($select.onSelectCallback) {
                $select.onSelectCallback(scope, {
                  $item: item,
                  $model: $select.parserResult.modelMapper(scope, locals),
                });
              }
            });
            scope.$selectMultiple.updateModel();
          }
        });
      };

      return $delegate;
    },
  ]);

  // The GCP nested approach throws an exception when activating the select when there's a nested select option,
  // but it's harmless so we trap it and discard it

  $provide.decorator('uiSelectMinErr', [
    '$delegate',
    function ($delegate: any) {
      return function handledError() {
        const original = $delegate;
        if (arguments.length === 3) {
          if (
            arguments[0] === 'choices' &&
            arguments[1] === `Expected multiple .ui-select-choices-row but got '{0}'.` &&
            arguments[2] === 0
          ) {
            // @ts-ignore
            throw new Error('IGNORE', 'IGNORE');
          }
        }
        return original.apply(this, arguments);
      };
    },
  ]);

  $provide.decorator('$exceptionHandler', [
    '$delegate',
    function ($delegate: any) {
      return function (exception: Error, cause: any) {
        if (exception && exception.message === 'IGNORE') {
          return;
        }

        if (Array.isArray(exception) && exception.length && exception[0] instanceof Error) {
          exception.forEach((e) => $delegate(e, cause));
        } else {
          $delegate(exception, cause);
        }
      };
    },
  ]);
}

bootstrapModule.config(uiSelectDecorator);

bootstrapModule.config([
  'uiSelectConfig',
  (uiSelectConfig: any) => {
    uiSelectConfig.theme = 'select2';
    uiSelectConfig.appendToBody = true;
  },
]);
