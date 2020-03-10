import { ICluster } from './ICluster';

export interface IArtifactExtractor {
  extractArtifacts: (cluster: ICluster) => string[];
  removeArtifact: (cluster: ICluster, artifactId: string) => void;
}
