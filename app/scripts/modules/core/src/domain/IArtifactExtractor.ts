import { ICluster } from 'core/domain';

export interface IArtifactExtractor {
  extractArtifacts: (cluster: ICluster) => string[];
  removeArtifact: (cluster: ICluster, artifactId: string) => void;
}
