import { cloneDeep, find, upperFirst } from 'lodash';

export interface IPreconditionTypeConfig {
  key: string;
  label: string;
}

export interface IPrecondition {
  cloudProvider?: string;
  context?: any;
  failPipeline?: boolean;
  type?: string;
}

const PRECONDITION_TYPES: IPreconditionTypeConfig[] = [
  { label: 'Cluster Size', key: 'clusterSize' },
  { label: 'Expression', key: 'expression' },
  { label: 'Stage Status', key: 'stageStatus' },
];

export function listPreconditionTypes(): IPreconditionTypeConfig[] {
  return cloneDeep(PRECONDITION_TYPES);
}

export function getPreconditionType(key?: string): IPreconditionTypeConfig | undefined {
  return find(PRECONDITION_TYPES, { key });
}

export function getPreconditionTypeLabel(key?: string): string {
  return getPreconditionType(key)?.label || upperFirst(key || '');
}
