import { IArtifact } from './IArtifact';
import { IMoniker } from '../naming/IMoniker';

export interface IManifest {
  name: string;
  moniker: IMoniker;
  account: string;
  cloudProvider: string;
  location: string;
  manifest: any;
  status: { [key: string]: IManifestStatus };
  artifacts: IArtifact[];
  events: IManifestEvent[];
}

export interface IManifestStatus {
  message?: string;
  state?: string;
  stable: boolean;
}

export interface IManifestEvent {
  apiVersion: string;
  count: number;
  firstTimestamp?: string;
  kind: string;
  lastTimestamp?: string;
  message?: string;
  reason: string;
  type: string;
}

export interface IJobOwnedPodStatus {
  name: string;
  status: any;
}
