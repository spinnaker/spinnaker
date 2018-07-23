import { ICluster } from 'core';

export interface IArtifactExtractor {
  extractArtifacts: (cluster: ICluster) => string[];
}
