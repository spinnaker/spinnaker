import { API } from 'core/api';
import { ISnapshot } from 'core/domain';

export class SnapshotReader {
  public static getSnapshotHistory(application: string, account: string, params = {}): PromiseLike<ISnapshot[]> {
    return API.path('applications')
      .path(application)
      .path('snapshots')
      .path(account)
      .path('history')
      .query(params)
      .get();
  }
}
