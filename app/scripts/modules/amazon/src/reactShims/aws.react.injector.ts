import IInjectorService = angular.auto.IInjectorService;

import { ReactInject } from '@spinnaker/core';

import { AmazonCertificateReader } from '../certificates/amazon.certificate.read.service';
import { AwsServerGroupTransformer } from '../serverGroup/serverGroup.transformer';
import { AwsLoadBalancerTransformer } from '../loadBalancer/loadBalancer.transformer';
import { VpcReader } from '../vpc/vpc.read.service';

export class AwsReactInject extends ReactInject {
  public get amazonCertificateReader() {
    return this.$injector.get('amazonCertificateReader') as AmazonCertificateReader;
  }
  public get autoScalingProcessService() {
    return this.$injector.get('autoScalingProcessService') as any;
  }
  public get awsLoadBalancerTransformer() {
    return this.$injector.get('awsLoadBalancerTransformer') as AwsLoadBalancerTransformer;
  }
  public get awsServerGroupCommandBuilder() {
    return this.$injector.get('awsServerGroupCommandBuilder') as any;
  }
  public get awsServerGroupTransformer() {
    return this.$injector.get('awsServerGroupTransformer') as AwsServerGroupTransformer;
  }
  public get vpcReader() {
    return this.$injector.get('vpcReader') as VpcReader;
  }
  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
  }
}

export const AwsReactInjector: AwsReactInject = new AwsReactInject();
