import { mockHttpClient } from '../api/mock/jasmine';
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
    const http = mockHttpClient({ autoFlush: true });
    http.expectGET('/slack/channels').respond(200, mockSlackChannels);

    const channels = await SlackReader.getChannels();
    expect(channels.length).toEqual(2);
  });
});
