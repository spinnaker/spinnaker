import {Observable} from 'rxjs';
import {module} from 'angular';

import {API_SERVICE, Api} from 'core/api/api.service';

export interface IPagerDutyService {
  name: string;
  integration_key: string;
}

export class PagerDutyReader {

  static get $inject() { return ['API']; }
  public constructor(private API: Api) {}

  public listServices(): Observable<IPagerDutyService[]> {
    return Observable.fromPromise(this.API.one('pagerDuty/services').useCache().getList());
  }
}

export const PAGER_DUTY_READ_SERVICE = 'spinnaker.netflix.pagerDuty.read.service';
module(PAGER_DUTY_READ_SERVICE, [API_SERVICE]).service('pagerDutyReader', PagerDutyReader);
