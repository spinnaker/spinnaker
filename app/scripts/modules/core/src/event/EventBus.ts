import { Observable, Subject } from 'rxjs';
import { filter, map } from 'rxjs/operators';

export interface IEventMessage {
  key: string;
  payload: any;
}

/**
 * Simple message bus, allowing events to be picked up across disparate components
 */
export class EventBus {
  private static message$: Subject<IEventMessage> = new Subject<IEventMessage>();

  /**
   * Creates a new observable of all future message payloads published with the specified key
   * @param {string} key
   * @return {Observable<any>}
   */
  public static observe<T>(key: string): Observable<T> {
    return this.message$.pipe(
      filter((m) => m.key === key),
      map((m) => m.payload),
    );
  }

  /**
   * Publishes a message with the supplied payload for any observer to pick up
   * @param {string} key
   * @param payload
   */
  public static publish(key: string, payload: any): void {
    this.message$.next({ key, payload });
  }
}
