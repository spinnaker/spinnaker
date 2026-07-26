import { mockHttpClient } from '../api/mock/jasmine';
import type { IPagerDutyService } from './pagerDuty.read.service';
import { PagerDutyReader } from './pagerDuty.read.service';

describe('PagerDutyReader', () => {
  it('should return an empty array when configured to do so and invoked', async () => {
    const http = mockHttpClient();
    const services: IPagerDutyService[] = [];
    http.expectGET(`/pagerDuty/services`).respond(200, services);

    let executed = false;
    PagerDutyReader.listServices().subscribe((pagerDutyServices: IPagerDutyService[]) => {
      expect(pagerDutyServices).toBeDefined();
      expect(pagerDutyServices.length).toBe(0);
      executed = true;
    });

    await http.flush();
    expect(executed).toBeTruthy();
  });

  it('should return a non-empty array when configured to do so and invoked', async () => {
    const http = mockHttpClient();
    const services: IPagerDutyService[] = [
      {
        name: 'one',
        integration_key: 'one_key',
        id: '1',
        policy: 'ABCDEF',
        lastIncidentTimestamp: '1970',
        status: 'active',
      },
      {
        name: '2',
        integration_key: 'two_key',
        id: '2',
        policy: 'ABCDEG',
        lastIncidentTimestamp: '1970',
        status: 'active',
      },
    ];
    http.expectGET(`/pagerDuty/services`).respond(200, services);

    let executed = false;
    PagerDutyReader.listServices().subscribe((pagerDutyServices: IPagerDutyService[]) => {
      expect(pagerDutyServices).toBeDefined();
      expect(pagerDutyServices.length).toBe(2);
      executed = true;
    });

    await http.flush();
    expect(executed).toBeTruthy();
  });
});
