import { IComponentOptions, IController, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';

import { EVENTS_CTRL, EventsController } from './events.controller';

class ViewEventsLinkCtrl implements IController {
  public serverGroup: any;

  public static $inject = ['$uibModal'];
  public constructor(private $uibModal: IModalService) {}

  public showEvents(): void {
    this.$uibModal.open({
      templateUrl: require('./events.html'),
      controller: EventsController,
      controllerAs: '$ctrl',
      windowClass: 'modal-z-index',
      resolve: {
        serverGroup: () => this.serverGroup,
      },
    });
  }
}

export const viewEventsLink: IComponentOptions = {
  bindings: {
    serverGroup: '<',
  },
  controller: ViewEventsLinkCtrl,
  template: `<a href ng-click="$ctrl.showEvents()">View Events</a>`,
};

export const VIEW_EVENTS_LINK_COMPONENT = 'spinnaker.ecs.serverGroup.details.viewEvents.link';
module(VIEW_EVENTS_LINK_COMPONENT, [EVENTS_CTRL]).component('viewEventsLink', viewEventsLink);
