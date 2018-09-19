import { IController, IComponentOptions, module } from 'angular';
import { IJsonDiff } from 'core/utils';

class DiffViewController implements IController {
  public diff: IJsonDiff;

  public scrollBody(e: MouseEvent): void {
    let line = 1;
    const target = e.target as HTMLElement;
    const currentTarget = e.currentTarget as HTMLElement;
    if (target.getAttribute('data-attr-block-line')) {
      line = parseInt(target.getAttribute('data-attr-block-line'), 10);
    } else {
      line = (e.offsetY / currentTarget.clientHeight) * this.diff.summary.total;
    }
    $('pre.history').animate({ scrollTop: (line - 3) * 15 }, 200);
  }
}

export const diffViewComponent: IComponentOptions = {
  bindings: {
    diff: '<',
  },
  controller: DiffViewController,
  template: `
      <pre class="form-control flex-fill diff"><div ng-repeat="line in $ctrl.diff.details"
             data-attr-line="{{$index}}"
             class="{{line.type}}">{{line.text}}</div></pre>
      <div class="summary-nav flex-fill"
           ng-click="$ctrl.scrollBody($event)">
        <div ng-repeat="block in $ctrl.diff.changeBlocks"
             data-attr-block-line="{{block.start}}"
             style="height: {{block.height}}%; top: {{block.top}}%"
             class="delta {{block.type}}">
        </div>
      </div>
`,
};

export const DIFF_VIEW_COMPONENT = 'spinnaker.core.pipeline.config.diffView.component';
module(DIFF_VIEW_COMPONENT, []).component('diffView', diffViewComponent);
