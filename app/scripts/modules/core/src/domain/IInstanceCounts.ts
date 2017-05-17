export interface IInstanceCounts {
  [k: string]: any; // Index Signature

  up: number;
  down: number;
  starting: number;
  succeeded: number;
  failed: number;
  unknown: number;
  outOfService: number;
}
