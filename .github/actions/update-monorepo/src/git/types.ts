import { ExecResult } from '../types';

export interface MergeResult {
  repo: string;
  isClean: boolean;
  exec: ExecResult;
}
