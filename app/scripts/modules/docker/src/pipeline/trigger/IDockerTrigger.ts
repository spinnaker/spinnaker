import { ITrigger } from '@spinnaker/core';

export interface IDockerTrigger extends ITrigger {
  account?: string;
  tag: string;
  registry?: string;
  repository: string;
  organization?: string;
}
