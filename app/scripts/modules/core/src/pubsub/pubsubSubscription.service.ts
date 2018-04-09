import { module, IPromise } from 'angular';

import { API_SERVICE, Api } from 'core/api/api.service';

export class PubsubSubscriptionService {
  constructor(private API: Api) {
    'ngInject';
  }

  public getPubsubSubscriptions(): IPromise<string[]> {
    return this.API.one('pubsub')
      .one('subscriptions')
      .get();
  }
}

export const PUBSUB_SUBSCRIPTION_SERVICE = 'spinnaker.core.pubsubSubscription.service';
module(PUBSUB_SUBSCRIPTION_SERVICE, [API_SERVICE]).service('pubsubSubscriptionService', PubsubSubscriptionService);
