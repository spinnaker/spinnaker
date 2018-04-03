import { IMoniker } from 'core/naming/IMoniker';
import { IArtifact } from 'core/domain';

export interface IManifest {
  name: string;
  moniker: IMoniker;
  account: string;
  cloudProvider: string;
  location: string;
  manifest: any;
  status: IManifestStatus;
  artifacts: IArtifact[];
  events: any[];
}

export interface IManifestStatus {
  message?: string;
  stable: boolean;
}

