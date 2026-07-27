import { REST } from '../api';
import type { ISnapshot } from '../domain';

export class SnapshotReader {
  public static getSnapshotHistory(application: string, account: string, params = {}): Promise<ISnapshot[]> {
    return REST('/applications').path(application, 'snapshots', account, 'history').query(params).get();
  }
}
