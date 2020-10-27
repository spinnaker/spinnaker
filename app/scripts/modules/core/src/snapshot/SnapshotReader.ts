

import { API } from 'core/api';
import { ISnapshot } from 'core/domain';

export class SnapshotReader {
  public static getSnapshotHistory(application: string, account: string, params = {}): PromiseLike<ISnapshot[]> {
    return API.one('applications')
      .one(application)
      .one('snapshots')
      .one(account)
      .one('history')
      .withParams(params)
      .getList();
  }
}
