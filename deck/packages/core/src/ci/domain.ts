export interface ICiBuildAPI {
  artifacts: Array<{ fileName: string; displayPath: string }>;
  building: boolean;
  duration: number;
  fullDisplayName: string;
  id: string;
  name: string;
  number: number;
  properties: {
    completedAt: string;
    completedTs: string;
    projectKey: string;
    pullRequestNumber: string;
    pullRequestUrl: string;
    repoSlug: string;
    startedAt: string;
    startedTs: string;
  };
  result: string;
  scm: Array<{
    branch: string;
    committer: string;
    compareUrl: string;
    sha1: string;
    message: string;
  }>;
  testResults: Array<{}>;
  timestamp: string;
  url: string;
}

export interface ICiBuild {
  author: string;
  artifacts: Array<{
    name: string;
    url: string;
  }>;
  branchName: string;
  commitId: string;
  commitLink: string;
  commitMessage: string;
  duration: number;
  fullDisplayName: string;
  id: string;
  isRunning: boolean;
  number: number;
  projectKey: string;
  pullRequestNumber: string;
  pullRequestUrl: string;
  repoLink?: string;
  repoSlug: string;
  result: string;
  startTime: number;
  url: string;
}

export interface ICiBuildOutputConfig {
  log: string;
}
