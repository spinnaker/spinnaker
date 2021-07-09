import { REST } from '../api';
import { IPubsubSubscription } from '../domain';

export class PubsubSubscriptionReader {
  public static getPubsubSubscriptions(): PromiseLike<IPubsubSubscription[]> {
    return REST('/pubsub/subscriptions').get();
  }
}
