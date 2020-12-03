import { REST } from 'core/api';
import { IPubsubSubscription } from 'core/domain';

export class PubsubSubscriptionReader {
  public static getPubsubSubscriptions(): PromiseLike<IPubsubSubscription[]> {
    return REST().path('pubsub', 'subscriptions').get();
  }
}
