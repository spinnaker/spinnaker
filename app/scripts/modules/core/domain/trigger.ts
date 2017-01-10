
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
}
