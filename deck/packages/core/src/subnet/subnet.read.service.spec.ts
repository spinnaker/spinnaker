import { mockHttpClient } from '../api/mock/jasmine';
import type { ISubnet } from '../domain';
import { SubnetReader } from './subnet.read.service';

describe('SubnetReader', function () {
  it('adds label to subnet, including (deprecated) if deprecated field is true', async function () {
    const http = mockHttpClient();
    http
      .expectGET('/subnets')
      .respond(200, [
        { purpose: 'internal', deprecated: true },
        { purpose: 'external', deprecated: false },
        { purpose: 'internal' },
      ]);

    const resultPromise = SubnetReader.listSubnets();

    await http.flush();
    const result: ISubnet[] = await resultPromise;

    expect(result[0].label).toBe('internal (deprecated)');
    expect(result[0].deprecated).toBe(true);
    expect(result[1].label).toBe('external');
    expect(result[1].deprecated).toBe(false);
    expect(result[2].label).toBe('internal');
    expect(result[2].deprecated).toBe(false);
  });
});
