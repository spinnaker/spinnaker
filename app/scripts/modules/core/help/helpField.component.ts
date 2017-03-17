import {Directive, ElementRef, Injector, Input} from '@angular/core';
import {UpgradeComponent} from '@angular/upgrade/static';
import {module} from 'angular';

import {HELP_CONTENTS_REGISTRY, HelpContentsRegistry} from './helpContents.registry';

interface IHelpFieldContents {
  content: string;
  placement: string;
}

class HelpFieldCtrl implements ng.IComponentController {

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
  private popoverClose: ng.IPromise<void>;

  static get $inject() {
    return ['$timeout', '$analytics', 'helpContents', 'helpContentsRegistry'];
  }

  constructor(private $timeout: ng.ITimeoutService,
              private $analytics: any,
              private helpContents: any,
              private helpContentsRegistry: HelpContentsRegistry) {}

  public $onInit(): void {
    if (!this.content && this.key) {
      this.content = this.helpContentsRegistry.getHelpField(this.key) || this.helpContents[this.key] || this.fallback;
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
      this.popoverClose = this.$timeout(
        () => {
          this.displayPopover = false;
        },
        300);
    } else {
      this.displayPopover = false;
    }
    // only track the event if the popover was on the screen for a little while, i.e. it wasn't accidentally
    // moused over
    if (Date.now() - this.popoverShownStart > 500) {
      this.$analytics.eventTrack('Help contents viewed', {category: 'Help', label: this.key || this.content});
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

class HelpFieldComponent implements ng.IComponentOptions {
  public bindings: any = {
    key: '@',
    fallback: '@',
    content: '@',
    placement: '@',
    expand: '=',
    label: '@'
  };
  public controller: any = HelpFieldCtrl;
  public template = `
    <div class="text-only" ng-if="$ctrl.label">
      <a href class="help-field" ng-if="!$ctrl.expand && $ctrl.contents.content"
              uib-popover-template="$ctrl.popoverTemplate"
              ng-mouseenter="$ctrl.showPopover()"
              ng-mouseleave="$ctrl.hidePopover(true)"
              popover-placement="{{$ctrl.contents.placement}}"
              popover-is-open="$ctrl.displayPopover"
              popover-trigger="none"
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
              popover-trigger="none">
        <span class="small glyphicon glyphicon-question-sign"></span>
      </a>
    </div>
  `;
}

const selector = 'helpField';
export const HELP_FIELD_COMPONENT = 'spinnaker.core.help.helpField.component';
module(HELP_FIELD_COMPONENT, [
  HELP_CONTENTS_REGISTRY,
  require('./helpContents'),
  require('angulartics'),
]).component(selector, new HelpFieldComponent());

@Directive({
  selector: 'help-field'
})
export class HelpFieldComponentDirective extends UpgradeComponent {

  @Input()
  public key: string;

  @Input()
  public fallback: string;

  @Input()
  public content: string;

  @Input()
  public placement: string;

  @Input()
  public expand: boolean;

  @Input()
  public label: string;

  constructor(elementRef: ElementRef, injector: Injector) {
    super(selector, elementRef, injector);
  }
}
