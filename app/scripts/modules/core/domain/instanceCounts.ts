export interface InstanceCounts {
  [k: string]: any; //Index Signature

  up: number;
  down: number;
  succeeded: number;
  failed: number;
  unknown: number;
  outOfService: number;
}
