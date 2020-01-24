import { IPromise } from 'angular';
import { API } from 'core';

export interface ISlackChannel {
  id: string;
  name: string;
  is_channel: boolean;
  creator: string;
  is_archived: boolean;
  name_normalized: string;
  num_members: number;
}

export class SlackReader {
  public static getChannels(): IPromise<ISlackChannel[]> {
    return API.one('slack/channels')
      .getList()
      .catch(() => [] as ISlackChannel[]);
  }
}
