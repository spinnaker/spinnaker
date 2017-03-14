import { module } from 'angular';
import {IModalService} from 'angular-ui-bootstrap';

import { DirectiveFactory } from 'core/utils/tsDecorators/directiveFactoryDecorator';

import './feedback.less';

// Making this a directive instead of a component because we need the <li> to replace
// the component to support bootstrap styling and can't just hoist the <li> out of the
// component because we style the li based on information in the controller..,
export class FeedbackController implements ng.IComponentController {
  static get $inject(): string[] {
    return ['$location', '$scope', '$uibModal', 'settings'];
  }

  constructor (private $location: ng.ILocationService,
               private $scope: any,
               private $uibModal: IModalService,
               private settings: any) {}

  public initialize(): void {
    this.$scope.slackConfig = this.settings.feedback ? this.settings.feedback.slack : null;

    this.$scope.state = {
      open: false,
      isMac: navigator.platform.toLowerCase().includes('mac'),
    };

    this.$scope.setOpen = (open: boolean) => {
      this.$scope.state.open = open;
    };

    this.$scope.getCurrentUrlMessage = () => {
      return encodeURIComponent('(via ' + this.$location.absUrl() + ')\n');
    };

    this.$scope.openFeedback = () => {
      this.$uibModal.open({
        templateUrl: require('./feedback.modal.html'),
        controller: 'FeedbackModalCtrl',
        controllerAs: 'ctrl'
      });
    };
  }
}

@DirectiveFactory()
class FeedbackDirective implements ng.IDirective {
  public restrict = 'E';
  public replace = true;
  public templateUrl = require('./feedback.html');
  public controller: any = FeedbackController;
  public controllerAs = '$ctrl';

  public link($scope: ng.IScope, _$element: JQuery) {
    const $ctrl: FeedbackController = $scope['$ctrl'];
    $ctrl.initialize();
  }
}

export const FEEDBACK_DIRECTIVE = 'spinnaker.netflix.feedback.directive';
module(FEEDBACK_DIRECTIVE, [
  require('core/config/settings')
]).directive('feedback', <any>FeedbackDirective);
