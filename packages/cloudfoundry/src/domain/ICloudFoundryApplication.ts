import { Application } from '@spinnaker/core';

export interface ICloudFoundryApplication extends Application {
  startApplication: boolean;
}
