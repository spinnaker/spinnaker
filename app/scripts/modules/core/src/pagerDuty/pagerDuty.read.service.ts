import { REST } from 'core/api/ApiService';
import { Observable } from 'rxjs';

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
    return Observable.fromPromise(REST('/pagerDuty/services').get());
  }

  public static listOnCalls(): Observable<{ [id: string]: IOnCall[] }> {
    return Observable.fromPromise(REST('/pagerDuty/oncalls').get());
  }
}
