import { IController, IComponentOptions, IScope, ITimeoutService, module } from 'angular';
import * as Clipboard from 'clipboard';

import './copyToClipboard.component.less';

export class CopyToClipboardController implements IController {
  public text: string;
  public toolTip: string;
  public tempToolTip: string;
  public analyticsLabel: string;

  public isOpen = false;

  public static $inject = ['$scope', '$element', '$timeout'];
  constructor(private $scope: IScope, private $element: JQuery, private $timeout: ITimeoutService) {
    'ngInject';
  }

  public $onInit(): void {
    // tslint:disable:no-unused-expression
    new Clipboard('.clipboard-btn');
    this.$element.on('click', () => {
      this.isOpen = true;
      this.toggleToolTipToCopied();
      this.$scope.$digest();
      this.$timeout(() => {
        this.isOpen = false;
        this.resetToolTip();
        this.$scope.$digest();
      }, 3000);
    });
  }

  public toggleToolTipToCopied(): void {
    this.tempToolTip = this.toolTip;
    this.toolTip = 'Copied';
  }

  public resetToolTip(): void {
    this.toolTip = this.tempToolTip;
  }
}

export class CopyToClipboardComponent implements IComponentOptions {
  public bindings: any = {
    text: '<',
    toolTip: '<',
    analyticsLabel: '<',
  };
  public controller: any = CopyToClipboardController;
  public template = `
      <button
        class="btn btn-xs btn-default clipboard-btn"
        uib-tooltip="{{$ctrl.toolTip}}"
        tooltip-trigger="mouseenter"
        tooltip-placement="top"
        tooltip-enable="true"
        tooltip-is-open="$ctrl.isOpen"
        analytics-on="click"
        analytics-category="Copy to Clipboard"
        analytics-event="{{$ctrl.toolTip}}"
        analytics-label="{{$ctrl.analyticsLabel || $ctrl.text}}"
        data-clipboard-action="copy"
        data-clipboard-text="{{$ctrl.text}}"
        aria-label="Copy to clipboard">
        <span class="glyphicon glyphicon-copy"></span>
      </button>`;
}

export const COPY_TO_CLIPBOARD_COMPONENT = 'spinnaker.core.utils.copyToClipboard.directive';
module(COPY_TO_CLIPBOARD_COMPONENT, []).component('copyToClipboard', new CopyToClipboardComponent());
