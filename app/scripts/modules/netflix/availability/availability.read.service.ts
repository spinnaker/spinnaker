import { module } from 'angular';

import {API_SERVICE, Api} from 'core/api/api.service';

export interface IAvailabilityDataTimeSeries {
  is_outage: number[];
  availability: number[];
  datetime: string[];
}

export interface IAvailabilityWindow {
  availability: number;
  nines: number;
  target_nines: number;
  date_range: Date[];
  ts: IAvailabilityDataTimeSeries;
  score?: number;
  [key: string]: any;
}

export interface IAvailabilityData {
  trends: IAvailabilityTrendsData;
  override: IAvailabilityOverride;
}

interface IAvailabilityOverride {
  value: boolean;
  reason: string;
}

export interface IAvailabilityTrendsData {
  last_updated: string;
  yesterday: IAvailabilityWindow;
  '28days': IAvailabilityWindow;
  '91days': IAvailabilityWindow;
  [key: string]: any;
}

export class AvailabilityReaderService {
  static get $inject(): string[] {
    return ['API'];
  }

  constructor (private API: Api) {}

  private parseResult (result: any): IAvailabilityData {
    if (result) {
      // convert date_range fields to Date objects so we can dress them up in the UI
      ['yesterday', '28days', '91days'].forEach((windowIndex: string) => {
        result.trends[windowIndex].date_range.forEach((date: string, dateIndex: number) => {
          result.trends[windowIndex].date_range[dateIndex] = new Date(date);
        });
      });
      // convert override value to boolean
      result.override.value = result.override.value === 'true' ? true : false;

      return result;
    }
    return null;
  }

  public getAvailabilityData(): ng.IPromise<IAvailabilityData> {
    return this.API.one('availability').get().then((result: any) => this.parseResult(result));
  }
}

export const AVAILABILITY_READER_SERVICE = 'spinnaker.netflix.availability.reader';
module(AVAILABILITY_READER_SERVICE, [
  API_SERVICE
]).service('availabilityReaderService', AvailabilityReaderService);
