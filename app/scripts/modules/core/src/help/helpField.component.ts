import { IController, IComponentOptions, IPromise, ITimeoutService, module } from 'angular';

import { HelpContentsRegistry } from './helpContents.registry';

export interface IHelpFieldContents {
  content: string;
  placement: string;
}

export class HelpFieldCtrl implements IController {
  public content: string;
  public expand: boolean;
  public label: string;
  public contents: IHelpFieldContents;
  public displayPopover: boolean;
  public popoverTemplate: string = require('./helpField.popover.html');

  private key: string;
  private fallback: string;
  private placement: string;
  private popoverShownStart: number;
  private popoverClose: IPromise<void>;

  public static $inject = ['$timeout', '$analytics'];
  constructor(private $timeout: ITimeoutService, private $analytics: any) {}

  public $onInit(): void {
    if (!this.content && this.key) {
      this.content = HelpContentsRegistry.getHelpField(this.key) || this.fallback;
    }
    this.contents = {
      content: this.content,
      placement: this.placement || 'auto',
    };
  }

  public $onChanges(): void {
    this.$onInit();
  }

  public $onDestroy(): void {
    if (this.popoverClose) {
      this.$timeout.cancel(this.popoverClose);
    }
  }

  public showPopover(): void {
    this.displayPopover = true;
    this.popoverShownStart = Date.now();
    this.popoverHovered();
  }

  public hidePopover(defer: boolean): void {
    if (defer) {
      this.popoverClose = this.$timeout(() => {
        this.displayPopover = false;
      }, 300);
    } else {
      this.displayPopover = false;
    }
    // only track the event if the popover was on the screen for a little while, i.e. it wasn't accidentally
    // moused over
    if (Date.now() - this.popoverShownStart > 500) {
      this.$analytics.eventTrack('Help contents viewed', { category: 'Help', label: this.key || this.content });
    }
    this.popoverShownStart = null;
  }

  public popoverHovered(): void {
    if (this.popoverClose) {
      this.$timeout.cancel(this.popoverClose);
      this.popoverClose = null;
    }
  }
}

export class HelpFieldComponent implements IComponentOptions {
  public bindings: any = {
    key: '@',
    fallback: '@',
    content: '@',
    placement: '@',
    expand: '=',
    label: '@',
  };
  public controller = HelpFieldCtrl;
  public template = `
    <div class="text-only" ng-if="$ctrl.label">
      <a href class="help-field" ng-if="!$ctrl.expand && $ctrl.contents.content"
              uib-popover-template="$ctrl.popoverTemplate"
              ng-mouseenter="$ctrl.showPopover()"
              ng-mouseleave="$ctrl.hidePopover(true)"
              popover-placement="{{$ctrl.contents.placement}}"
              popover-is-open="$ctrl.displayPopover"
              popover-trigger="'none'"
              ng-bind-html="$ctrl.label">
      </a>
    </div>
    <div ng-if="!$ctrl.label" style="display: inline-block;">
      <div ng-if="$ctrl.expand && $ctrl.contents.content"
           class="help-contents small"
           ng-bind-html="$ctrl.contents.content"></div>
      <a href class="help-field" ng-if="!$ctrl.expand && $ctrl.contents.content"
              uib-popover-template="$ctrl.popoverTemplate"
              ng-mouseenter="$ctrl.showPopover()"
              ng-mouseleave="$ctrl.hidePopover(true)"
              popover-placement="{{$ctrl.contents.placement}}"
              popover-is-open="$ctrl.displayPopover"
              popover-trigger="'none'">
        <i class="small glyphicon glyphicon-question-sign"></i>
      </a>
    </div>
  `;
}

export class HelpFieldWrapperComponent implements IComponentOptions {
  public bindings: any = {
    id: '<',
    fallback: '<',
    content: '<',
    placement: '<',
    expand: '<',
    label: '<',
  };
  public template = `<help-field content="{{::$ctrl.content}}"
                                 key="{{::$ctrl.id}}"
                                 fallback="{{$ctrl.fallback}}"
                                 placement="{{$ctrl.placement}}"
                                 expand="$ctrl.expand"
                                 label="{{$ctrl.label}}"></help-field>`;
}

export const HELP_FIELD_COMPONENT = 'spinnaker.core.help.helpField.component';
module(HELP_FIELD_COMPONENT, [require('angulartics')])
  .component('helpField', new HelpFieldComponent())
  .component('helpFieldWrapper', new HelpFieldWrapperComponent());
