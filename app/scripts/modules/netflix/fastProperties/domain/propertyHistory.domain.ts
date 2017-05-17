import { IJsonDiff } from '@spinnaker/core';

export interface IPropertyHistoryEntry {
  timestamp: number;
  sourceOfUpdate: string;
  cmc: string;
  updatedBy: string;
  action: string;
  comment: string;
  diff?: IJsonDiff;
}
