import { module } from 'angular';

import { AMAZON_CERTIFICATE_READ_SERVICE } from '../certificates/amazon.certificate.read.service';
import { AwsNgReact } from './aws.ngReact';
import { AwsReactInjector } from './aws.react.injector';

export const AWS_REACT_MODULE = 'spinnaker.amazon.react';
module(AWS_REACT_MODULE, [
  AMAZON_CERTIFICATE_READ_SERVICE, // only used by react components
]).run(function($injector: any) {
  // Make angular services importable and (TODO when relevant) convert angular components to react
  AwsReactInjector.initialize($injector);
  AwsNgReact.initialize($injector);
});
