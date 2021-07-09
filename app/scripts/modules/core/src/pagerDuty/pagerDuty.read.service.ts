import { from as observableFrom, Observable } from 'rxjs';

import { REST } from '../api/ApiService';

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
    return observableFrom(REST('/pagerDuty/services').get());
  }

  public static listOnCalls(): Observable<{ [id: string]: IOnCall[] }> {
    return observableFrom(REST('/pagerDuty/oncalls').get());
  }
}
