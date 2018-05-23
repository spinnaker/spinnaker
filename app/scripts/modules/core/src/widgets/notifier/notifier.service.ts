import { Subject } from 'rxjs';

export interface INotification {
  key: string;
  action: 'remove' | 'create';
  body?: string;
}

export class NotifierService {
  private static stream = new Subject<INotification>();

  public static get messageStream(): Subject<INotification> {
    return this.stream;
  }

  public static publish(message: INotification): void {
    this.stream.next(message);
  }

  public static clear(key: string): void {
    this.stream.next({ action: 'remove', key });
  }
}
