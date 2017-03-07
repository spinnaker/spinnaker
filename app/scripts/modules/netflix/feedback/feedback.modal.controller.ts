import { module } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';

import { AUTHENTICATION_SERVICE, AuthenticationService } from 'core/authentication/authentication.service';

interface IFeedback {
  title: string;
  description: string;
  contact: string;
}

class FeedbackModalController implements ng.IComponentController {
  public states = {
    EDITING: 0,
    SUBMITTING: 1,
    SUBMITTED: 2,
    ERROR: 3
  };
  public state: number = this.states.EDITING;
  public userIsAuthenticated: boolean;
  public feedback: IFeedback;
  public issueUrl: string;
  public issueId: string;

  static get $inject () {
    return [
      '$location',
      '$http',
      '$uibModalInstance',
      'settings',
      'authenticationService'
    ];
  }

  constructor (private $location: ng.ILocationService,
               private $http: ng.IHttpService,
               private $uibModalInstance: IModalServiceInstance,
               private settings: any,
               private authenticationService: AuthenticationService) {}

  public $onInit (): void {
    this.userIsAuthenticated = this.authenticationService.getAuthenticatedUser().authenticated;
    this.feedback = {
      title: '',
      description: '',
      contact: ''
    };
  }

  private getContactInfo(): string {
    if (this.userIsAuthenticated) {
      return this.authenticationService.getAuthenticatedUser().name;
    }
    return this.feedback.contact;
  }

  private getUserNameFromContactInfo(): string {
    const email = this.getContactInfo();
    if (email.includes('@')) {
      return email.split('@')[0];
    }
    return email;
  }

  private buildDescription(): string {
    return [
      '*Submitted by:*\n' + this.getContactInfo(),
      '*From page:*\n' + this.$location.absUrl(),
      '*Description:*\n' + this.feedback.description
    ].join('\n\n');
  }

  private buildRequestBody(): IFeedback {
    return {
      title: this.feedback.title,
      description: this.buildDescription(),
      contact: this.getUserNameFromContactInfo(),
    };
  }

  public submit(): void {
    this.state = this.states.SUBMITTING;
    this.$http.post(this.settings.feedbackUrl, this.buildRequestBody())
      .then((result: any) => {
        this.state = this.states.SUBMITTED;
        this.issueUrl = result.data.url;
        this.issueId = result.data.id;
      },
      () => {
        this.state = this.states.ERROR;
      });
  };

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }
}

export const FEEDBACK_MODAL_CONTROLLER = 'spinnaker.netflix.feedback.modal.controller';
module(FEEDBACK_MODAL_CONTROLLER, [
  require('core/config/settings'),
  AUTHENTICATION_SERVICE
])
.controller('FeedbackModalCtrl', FeedbackModalController);
