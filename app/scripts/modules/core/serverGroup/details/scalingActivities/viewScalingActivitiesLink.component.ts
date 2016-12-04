import IModalService = angular.ui.bootstrap.IModalService;
import {SCALING_ACTIVITIES_CTRL, ScalingActivitiesCtrl} from './scalingActivities.controller';

class ViewScalingActivitiesLinkCtrl implements ng.IComponentController {
  public serverGroup: any;

  static get $inject() { return ['$uibModal']; }

  public constructor(private $uibModal: IModalService) {}

  public showScalingActivities(): void {
    this.$uibModal.open({
      templateUrl: require('./scalingActivities.html'),
      controller: ScalingActivitiesCtrl,
      controllerAs: '$ctrl',
      resolve: {
        serverGroup: () => this.serverGroup
      }
    });
  }
}

class ViewScalingActivitiesLink implements ng.IComponentOptions {
  public bindings: any = {
    serverGroup: '='
  };
  public controller: ng.IComponentController = ViewScalingActivitiesLinkCtrl;
  public template: string = `<a href ng-click="$ctrl.showScalingActivities()">View Scaling Activities</a>`;
}

export const VIEW_SCALING_ACTIVITIES_LINK = 'spinnaker.core.serverGroup.details.viewScalingActivities.link';

angular.module(VIEW_SCALING_ACTIVITIES_LINK, [
    SCALING_ACTIVITIES_CTRL,
  ])
  .component('viewScalingActivitiesLink', new ViewScalingActivitiesLink());
