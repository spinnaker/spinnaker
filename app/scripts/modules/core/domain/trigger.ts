
import {Execution} from "./execution";

export interface Trigger {
  user: string;
  parentExecution: Execution;
  type: string;
}

export interface IGitTrigger extends Trigger {
  source: string;
  project: string;
  slug: string;
  branch: string;
  type: 'git';
}

export interface IJenkinsTrigger extends Trigger {
  job: string;
  master: string;
  type: 'jenkins';
}
