import type { IJenkinsInfo } from './IJenkinsInfo';

export interface IBuildDiffInfo {
  ancestor: string;
  jenkins?: IJenkinsInfo;
  target: string;
}
