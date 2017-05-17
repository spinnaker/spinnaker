import { module } from 'angular';
import { Observable } from 'rxjs';

import { Api, API_SERVICE } from '@spinnaker/core';

export interface IPagerDutyService {
  name: string;
  integration_key: string;
}

export class PagerDutyReader {
  public constructor(private API: Api) { 'ngInject'; }

  public listServices(): Observable<IPagerDutyService[]> {
    return Observable.fromPromise(this.API.one('pagerDuty/services').getList());
  }
}

export const PAGER_DUTY_READ_SERVICE = 'spinnaker.netflix.pagerDuty.read.service';
module(PAGER_DUTY_READ_SERVICE, [API_SERVICE]).service('pagerDutyReader', PagerDutyReader);
