import { API } from '@spinnaker/core';
import { ISlackChannel, SlackReader } from './SlackReader';

const mockSlackChannels: ISlackChannel[] = [
  {
    id: '0-test',
    name: 'testchannel',
    is_channel: true,
    creator: 'creator',
    is_archived: false,
    name_normalized: 'testchannel',
    num_members: 25,
  },
  {
    id: '1-test',
    name: 'fakechannel',
    is_channel: true,
    creator: 'creator2',
    is_archived: false,
    name_normalized: 'fakechannel',
    num_members: 25,
  },
];

describe('SlackReader', () => {
  it('should fetch a list of public Slack channels', async () => {
    spyOn(SlackReader, 'getChannels').and.callThrough();
    spyOn(API, 'one')
      .and.callThrough()
      .and.returnValue({ getList: () => Promise.resolve(mockSlackChannels) });

    await SlackReader.getChannels().then((channels: ISlackChannel[]) => {
      expect(SlackReader.getChannels).toHaveBeenCalled();
      expect(channels.length).toEqual(2);
      expect(API.one).toHaveBeenCalledWith('slack', 'channels');
    });
  });
});
