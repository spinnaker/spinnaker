
import {Execution} from "./execution";

export interface Trigger {
  user: string;
  parentExecution: Execution;
}
