import { REST } from '../api';
import type { IPubsubSubscription } from '../domain';

export class PubsubSubscriptionReader {
  public static getPubsubSubscriptions(): PromiseLike<IPubsubSubscription[]> {
    return REST('/pubsub/subscriptions').get();
  }
}
