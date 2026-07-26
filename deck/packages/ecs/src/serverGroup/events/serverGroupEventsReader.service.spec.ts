import { mockHttpClient } from '../../../../core/src/api/mock/jasmine';

import { ServerGroupEventsReader } from './serverGroupEventsReader.service';

describe('ServerGroupEventsReader', () => {
  const serverGroup = {
    app: 'fnord',
    account: 'test',
    name: 'fnord-main-v001',
    region: 'us-east-1',
    cloudProvider: 'ecs',
  } as any;

  it('reads ECS server group events', async () => {
    const http = mockHttpClient();
    const events = [{ id: 'one', createdAt: 1, message: 'steady state', status: 'Success' }];
    http
      .expectGET('/applications/fnord/serverGroups/test/fnord-main-v001/events')
      .withParams({ region: 'us-east-1', provider: 'ecs' })
      .respond(200, events);

    const result = ServerGroupEventsReader.getEvents(serverGroup);
    await http.flush();

    await expectAsync(Promise.resolve(result)).toBeResolvedTo(events);
  });

  it('rejects when the events request fails', async () => {
    const http = mockHttpClient();
    http
      .expectGET('/applications/fnord/serverGroups/test/fnord-main-v001/events')
      .withParams({ region: 'us-east-1', provider: 'ecs' })
      .respond(500, { message: 'failed' });

    const result = Promise.resolve(ServerGroupEventsReader.getEvents(serverGroup));
    const expectation = expectAsync(result).toBeRejected();
    await http.flush();

    await expectation;
  });
});
