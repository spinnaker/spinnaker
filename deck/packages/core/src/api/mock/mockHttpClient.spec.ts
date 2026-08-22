import { MockHttpClient } from './mockHttpClient';

describe('MockHttpClient', () => {
  beforeAll(() => jasmine.clock().install());
  afterAll(() => jasmine.clock().uninstall());

  it('clears its timeout watchdog as soon as all expected requests are flushed', async () => {
    const clearTimeout = spyOn(window, 'clearTimeout').and.callThrough();
    const http = new MockHttpClient();
    http.expectGET('/tasks/1').respond(200, { status: 'SUCCEEDED' });
    const response = http.get({ url: '/tasks/1', params: {}, data: undefined } as any);

    const flush = http.flush();

    expect(clearTimeout).toHaveBeenCalledTimes(1);
    jasmine.clock().tick(0);
    await flush;
    await expectAsync(Promise.resolve(response)).toBeResolvedTo({ status: 'SUCCEEDED' });
  });

  it('times out deterministically when an expected request is not received', async () => {
    const http = new MockHttpClient();
    http.expectGET('/tasks/1');

    const flush = http.flush({ timeoutMs: 50 });
    const rejection = expectAsync(flush).toBeRejectedWith(
      'MockHttpClient.flush() timed out after 50ms\n' +
        '1 outstanding requests.\n' +
        'The following HTTP calls were expected, but were not received:\n' +
        '\t- HTTP GET /tasks/1',
    );
    jasmine.clock().tick(50);

    await rejection;
  });
});
