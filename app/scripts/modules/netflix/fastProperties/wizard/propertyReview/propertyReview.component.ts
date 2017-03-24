import { module } from 'angular';

class FastPropertyDetailsComponent implements ng.IComponentOptions {
  public templateUrl: string = require('./propertyReview.component.html');
  public bindings: any = {
    command: '='
  };
}

export const FAST_PROPERTY_REVIEW_COMPONENT = 'spinnaker.netflix.fastProperties.review.component';

module(FAST_PROPERTY_REVIEW_COMPONENT, [
])
  .component('fastPropertyReview', new FastPropertyDetailsComponent());
