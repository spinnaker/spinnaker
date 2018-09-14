import { ICloudFoundrySpace } from 'cloudfoundry/domain';

export interface ICloudFoundryDroplet {
  id: string;
  name: string;
  space: ICloudFoundrySpace;
  stack: string;
  buildpacks: ICloudFoundryBuildpack[];
  sourcePackage: ICloudFoundryPackage;
  packageChecksum: string;
}

export interface ICloudFoundryBuildpack {
  name: string;
  detectOutput: string;
  version: string;
  buildpackName: string;
}

export interface ICloudFoundryPackage {
  downloadUrl: string;
}
