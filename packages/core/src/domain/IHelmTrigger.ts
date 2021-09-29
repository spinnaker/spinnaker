import type { ITrigger } from './ITrigger';

export interface IHelmTrigger extends ITrigger {
  account?: string;
  artifactName: string;
  chart: string;
  version: string;
  digest: string;
}
