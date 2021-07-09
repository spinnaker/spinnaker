/** based on http://jsfiddle.net/epinapala/WdeTM/4/ */
import { IController, IScope, module } from 'angular';

class DoubleClickController implements IController {
  public $element: JQuery;
  public $scope: IScope;
  public $attrs: any;
  private BOUND_EVENT = 'dblclick.textselection';

  public selectText(): void {
    const selection = window.getSelection();
    const range: Range = window.document.createRange();
    range.selectNodeContents(this.$element.get(0));
    selection.removeAllRanges();
    selection.addRange(range);
  }

  public initialize(): void {
    this.$element.bind(this.BOUND_EVENT, () => this.selectText());
    this.$scope.$on('$destroy', () => this.$element.unbind(this.BOUND_EVENT));
  }
}

export const SELECT_ON_DOUBLE_CLICK_DIRECTIVE = 'spinnaker.core.utils.selectOnDblClick';
module(SELECT_ON_DOUBLE_CLICK_DIRECTIVE, []).directive('selectOnDblClick', function () {
  return {
    restrict: 'A',
    controller: DoubleClickController,
    link: ($scope: IScope, $element: JQuery, _$attrs: any, ctrl: DoubleClickController) => {
      ctrl.$scope = $scope;
      ctrl.$element = $element;
      ctrl.$attrs = _$attrs;
      ctrl.initialize();
    },
  };
});
