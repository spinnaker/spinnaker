import IInjectorService = angular.auto.IInjectorService;

import { ReactInject } from '@spinnaker/core';

import { AmazonCertificateReader } from '../certificates/amazon.certificate.read.service';
import { AwsServerGroupTransformer } from '../serverGroup/serverGroup.transformer';
import { AwsLoadBalancerTransformer } from '../loadBalancer/loadBalancer.transformer';

export class AwsReactInject extends ReactInject {

  public get amazonCertificateReader() { return this.$injector.get('amazonCertificateReader') as AmazonCertificateReader; }
  public get awsServerGroupTransformer() { return this.$injector.get('awsServerGroupTransformer') as AwsServerGroupTransformer; }
  public get awsLoadBalancerTransformer() { return this.$injector.get('awsLoadBalancerTransformer') as AwsLoadBalancerTransformer; }

  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
  }

}

export const AwsReactInjector: AwsReactInject = new AwsReactInject();
