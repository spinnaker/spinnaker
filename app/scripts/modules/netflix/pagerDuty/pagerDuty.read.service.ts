import {Observable} from 'rxjs';
import {Injectable, Inject} from '@angular/core';

import {Api} from 'core/api/api.service';

export interface IPagerDutyService {
  name: string;
  integration_key: string;
}

@Injectable()
export class PagerDutyReader {

  public constructor(@Inject('API') private API: Api) {}

  public listServices(): Observable<IPagerDutyService[]> {
    return Observable.fromPromise(this.API.one('pagerDuty/services').useCache().getList());
  }
}
