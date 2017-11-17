import { IMoniker } from 'core/naming/IMoniker';

export interface IManifest {
  name: string;
  moniker: IMoniker;
  account: string;
  cloudProvider: string;
  location: string;
  manifest: any;
  status: IManifestStatus;
}

export interface IManifestStatus {
  message?: string;
  stable: boolean;
}

