import { ICloudFoundrySpace } from './ICloudFoundrySpace';

export interface ICloudFoundryDroplet {
  id: string;
  name: string;
  space: ICloudFoundrySpace;
  stack: string;
  buildpacks: ICloudFoundryBuildpack[];
  sourcePackage?: ICloudFoundryPackage;
}

export interface ICloudFoundryBuildpack {
  name: string;
  detectOutput: string;
  version: string;
  buildpackName: string;
}

export interface ICloudFoundryPackage {
  checksum: string;
  downloadUrl: string;
}
