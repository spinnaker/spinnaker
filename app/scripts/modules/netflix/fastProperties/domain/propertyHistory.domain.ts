import { IJsonDiff } from 'core/utils/json/json.utility.service';

export interface IPropertyHistoryEntry {
  timestamp: number;
  sourceOfUpdate: string;
  cmc: string;
  updatedBy: string;
  action: string;
  comment: string;
  diff?: IJsonDiff;
}
