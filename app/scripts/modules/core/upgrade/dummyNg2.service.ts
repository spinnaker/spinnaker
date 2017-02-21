import {Injectable} from '@angular/core';

import {IDowngradeItem} from 'core/domain/IDowngradeItem';

@Injectable()
export class DummyNg2Service {

  private message = 'Dummy NG2 Service';

  public getMessage(): string {
    return this.message;
  }
}

export const DUMMY_NG2_SERVICE = 'dummy.ng2.service.module';
export const DUMMY_DOWNGRADE: IDowngradeItem = {
  moduleName: DUMMY_NG2_SERVICE,
  injectionName: 'dummyNg2Service',
  moduleClass: DummyNg2Service
};
