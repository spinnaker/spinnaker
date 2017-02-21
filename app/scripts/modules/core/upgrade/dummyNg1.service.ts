import {module} from 'angular';

import {DummyNg2Service} from 'core/upgrade/dummyNg2.service';

export class DummyNg1Service {

  private message = 'Dummy NG1 Service';

  static get $inject() {
    return ['dummyNg2Service'];
  }

  constructor(private dummyNg2Service: DummyNg2Service) {}

  public getMessage(): string {
    return this.message;
  }

  public getInjectedMessage(): string {
    return this.dummyNg2Service.getMessage();
  }
}

export const DUMMY_NG1_SERVICE = 'dummy.ng1.service.module';
module(DUMMY_NG1_SERVICE, []).service('dummyNg1Service', DummyNg1Service);
