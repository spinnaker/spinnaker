import { IPromise } from 'angular';

import { API, IPubsubSubscription } from '@spinnaker/core';

export class PubsubSubscriptionReader {
  public static getPubsubSubscriptions(): IPromise<IPubsubSubscription[]> {
    return API.one('pubsub')
      .one('subscriptions')
      .get();
  }
}
