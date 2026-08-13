import { mockHttpClient } from '../../../../core/src/api/mock/jasmine';

import { listCanaryExecutions } from './canaryRun.service';

describe('listCanaryExecutions', () => {
  it('uses the explicitly provided route count', async () => {
    const http = mockHttpClient({ autoFlush: true });
    http.expectGET('/v2/canaries/deck/executions').withParams({ limit: 50 }, true).respond(200, []);

    await listCanaryExecutions('deck', { params: { count: 50 } });
  });

  it('defaults the execution count when the route omits it', async () => {
    const http = mockHttpClient({ autoFlush: true });
    http.expectGET('/v2/canaries/deck/executions').withParams({ limit: 20 }, true).respond(200, []);

    await listCanaryExecutions('deck', { params: {} });
  });
});
