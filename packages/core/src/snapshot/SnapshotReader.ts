import { REST } from '../api';
import { ISnapshot } from '../domain';

export class SnapshotReader {
  public static getSnapshotHistory(application: string, account: string, params = {}): PromiseLike<ISnapshot[]> {
    return REST('/applications').path(application, 'snapshots', account, 'history').query(params).get();
  }
}
