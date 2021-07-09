import { REST } from '../index';

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
  public static getChannels(): PromiseLike<ISlackChannel[]> {
    return REST('/slack/channels')
      .get()
      .catch(() => [] as ISlackChannel[]);
  }
}
