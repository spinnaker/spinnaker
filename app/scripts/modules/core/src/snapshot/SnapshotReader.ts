import { REST } from 'core/api';
import { ISnapshot } from 'core/domain';

export class SnapshotReader {
  public static getSnapshotHistory(application: string, account: string, params = {}): PromiseLike<ISnapshot[]> {
    return REST().path('applications', application, 'snapshots', account, 'history').query(params).get();
  }
}
