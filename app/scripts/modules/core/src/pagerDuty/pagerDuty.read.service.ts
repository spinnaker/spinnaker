import { Observable } from 'rxjs';

import { API } from 'core/api/ApiService';

export interface IPagerDutyService {
  id: string;
  integration_key: string;
  lastIncidentTimestamp: string;
  name: string;
  policy: string;
  status: string;
}

export interface IOnCall {
  escalation_policy: {
    id: string;
  };
  escalation_level: number;
  user: {
    summary: string;
    html_url: string;
  };
}

export class PagerDutyReader {
  public static listServices(): Observable<IPagerDutyService[]> {
    return Observable.fromPromise(API.one('pagerDuty', 'services').getList());
  }

  public static listOnCalls(): Observable<{ [id: string]: IOnCall[] }> {
    return Observable.fromPromise(API.one('pagerDuty', 'oncalls').getList());
  }
}
