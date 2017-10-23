import { ISCEService, module } from 'angular';
import { Subject } from 'rxjs';

export interface INotification {
  body?: string;
  action?: string;
  position?: string;
  key?: string;
}

export class NotifierService {

  private stream = new Subject<INotification>();

  public get messageStream(): Subject<INotification> {
    return this.stream;
  }

  constructor(private $sce: ISCEService) { 'ngInject'; }

  public publish(message: INotification): void {
    message.body = this.$sce.trustAsHtml(message.body);
    this.stream.next(message);
  }

  public clear(key: string): void {
    this.stream.next({ action: 'remove', key });
  }
}

export const NOTIFIER_SERVICE = 'spinnaker.core.widgets.notifier.service';
module(NOTIFIER_SERVICE, []).service('notifierService', NotifierService);
