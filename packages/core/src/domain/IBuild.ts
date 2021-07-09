export interface IBuildArtifact {
  displayPath: string;
  fileName: string;
  relativePath: string;
}

export interface IBuild {
  building: boolean;
  duration: number;
  name: string;
  number: number;
  result: string;
  timestamp: Date;
  url: string;
  artifacts: IBuildArtifact[];
}
