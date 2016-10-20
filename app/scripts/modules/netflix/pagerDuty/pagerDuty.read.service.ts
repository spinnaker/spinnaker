import {module} from 'angular';
import {API_SERVICE, Api} from '../../core/api/api.service';

export interface IPagerDutyService {
  name: string;
  integration_key: string;
}

export class PagerDutyReader {
  static get $inject() { return ['API']; }

  public constructor(private API: Api) {}

  public listServices(): ng.IPromise<[IPagerDutyService]> {
    return this.API.one('pagerDuty/services').useCache().getList();
  }
}

const moduleName = 'spinnaker.netflix.pagerDuty.read.service';

module(moduleName, [
  API_SERVICE
]).service('pagerDutyReader', PagerDutyReader);

export default moduleName;
