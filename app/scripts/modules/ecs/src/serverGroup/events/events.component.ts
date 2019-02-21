import { IController, IComponentOptions, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { EVENTS_CTRL, EventsController } from './events.controller';

class ViewEventsLinkCtrl implements IController {
  public serverGroup: any;

  public static $inject = ['$uibModal'];
  public constructor(private $uibModal: IModalService) {
    'ngInject';
  }

  public showEvents(): void {
    this.$uibModal.open({
      templateUrl: require('./events.html'),
      controller: EventsController,
      controllerAs: '$ctrl',
      resolve: {
        serverGroup: () => this.serverGroup,
      },
    });
  }
}

export class ViewEventsLink implements IComponentOptions {
  public bindings: any = {
    serverGroup: '<',
  };
  public controller: any = ViewEventsLinkCtrl;
  public template = `<a href ng-click="$ctrl.showEvents()">View Events</a>`;
}

export const VIEW_EVENTS_LINK_COMPONENT = 'spinnaker.ecs.serverGroup.details.viewEvents.link';
module(VIEW_EVENTS_LINK_COMPONENT, [EVENTS_CTRL]).component('viewEventsLink', new ViewEventsLink());
