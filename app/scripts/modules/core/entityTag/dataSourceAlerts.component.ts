import {module} from 'angular';
import {IEntityTag} from 'core/domain/IEntityTags';

class DataSourceAlertsCtrl implements ng.IComponentController {

  public alerts: IEntityTag[] = [];

  public popoverTemplate: string = require('./dataSourceAlerts.popover.html');
  public displayPopover: boolean;

  private popoverClose: ng.IPromise<void>;

  static get $inject() { return ['$timeout']; }

  public constructor(private $timeout: ng.ITimeoutService) {}

  // Popover bits allow the popover to stay open when hovering to allow users to click on links, highlight text, etc.
  // We may end up extracting this into a common widget if we want to use it elsewhere

  public showPopover(): void {
    this.displayPopover = true;
    this.popoverHovered();
  }

  public popoverHovered(): void {
    if (this.popoverClose) {
      this.$timeout.cancel(this.popoverClose);
      this.popoverClose = null;
    }
  }

  public hidePopover(defer: boolean): void {
    if (defer) {
      this.popoverClose = this.$timeout(() => {
        this.displayPopover = false;
      }, 500);
    } else {
      this.displayPopover = false;
    }
  }

}

class DataSourceAlertsComponent implements ng.IComponentOptions {
  public bindings: any = {
    alerts: '=',
  };
  public controller: any = DataSourceAlertsCtrl;
  public template = `
    <span ng-if="$ctrl.alerts.length > 0"
          class="tag-marker small" 
          ng-mouseover="$ctrl.showPopover()" 
          ng-mouseleave="$ctrl.hidePopover(true)">
      <span uib-popover-template="$ctrl.popoverTemplate"
            popover-placement="bottom"
            popover-trigger="none"
            popover-is-open="$ctrl.displayPopover"
            popover-class="no-padding">
        <i class="fa fa-exclamation-triangle"></i>
      </span>
    </span>  
  `;
}

export const DATA_SOURCE_ALERTS_COMPONENT = 'spinnaker.core.entityTag.dataSourceAlerts.component';
module(DATA_SOURCE_ALERTS_COMPONENT, [])
  .component('dsAlerts', new DataSourceAlertsComponent()); // "ds" because "data" is parsed differently by Angular :(
