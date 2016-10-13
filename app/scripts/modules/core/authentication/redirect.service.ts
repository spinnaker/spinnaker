import {module} from 'angular';

export class RedirectService {

  static get $inject(): string[] {
    return ['$window'];
  }

  constructor(private $window: ng.IWindowService) {}

  public redirect(url: string): void {
    this.$window.location.href = url;
  }
}

export const REDIRECT_SERVICE = 'spinnaker.redirect.service';
module(REDIRECT_SERVICE, [])
  .service('redirectService', RedirectService);
