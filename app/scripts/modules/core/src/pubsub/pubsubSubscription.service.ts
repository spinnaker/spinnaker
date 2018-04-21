import { module, IPromise } from 'angular';

import { API } from 'core/api/ApiService';

export class PubsubSubscriptionService {
  public getPubsubSubscriptions(): IPromise<string[]> {
    return API.one('pubsub')
      .one('subscriptions')
      .get();
  }
}

export const PUBSUB_SUBSCRIPTION_SERVICE = 'spinnaker.core.pubsubSubscription.service';
module(PUBSUB_SUBSCRIPTION_SERVICE, []).service('pubsubSubscriptionService', PubsubSubscriptionService);
