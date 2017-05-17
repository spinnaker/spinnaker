import {module} from 'angular';

export class RedirectService {

  constructor(private $window: ng.IWindowService) { 'ngInject'; }

  public redirect(url: string): void {
    this.$window.location.href = url;
  }
}

export const REDIRECT_SERVICE = 'spinnaker.redirect.service';
module(REDIRECT_SERVICE, [])
  .service('redirectService', RedirectService);
