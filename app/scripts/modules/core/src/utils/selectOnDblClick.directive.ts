/** based on http://jsfiddle.net/epinapala/WdeTM/4/  **/
import { IComponentOptions, IDirective, IScope, module } from 'angular';
import { DirectiveFactory } from './tsDecorators/directiveFactoryDecorator';

class DoubleClickController implements IComponentOptions {
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

@DirectiveFactory()
class DoubleClickDirective implements IDirective {
  public restrict = 'A';
  public controller: any = DoubleClickController;
  public bindToController = {};

  public link($scope: IScope, $element: JQuery, _$attrs: any, ctrl: DoubleClickController) {
    ctrl.$scope = $scope;
    ctrl.$element = $element;
    ctrl.$attrs = _$attrs;
    ctrl.initialize();
  }
}

export const SELECT_ON_DOUBLE_CLICK_DIRECTIVE = 'spinnaker.core.utils.selectOnDblClick';
module(SELECT_ON_DOUBLE_CLICK_DIRECTIVE, [])
  .directive('selectOnDblClick', <any>DoubleClickDirective);
