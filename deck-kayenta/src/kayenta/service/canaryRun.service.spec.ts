const mockGet = jest.fn(() => Promise.resolve([]));
const mockQuery = jest.fn(() => ({ get: mockGet }));
const mockPath = jest.fn(() => ({ query: mockQuery }));

jest.mock('@spinnaker/core', () => ({
  PipelineConfigService: {},
  REST: jest.fn(() => ({ path: mockPath })),
}));
jest.mock('kayenta/canary.settings', () => ({ CanarySettings: {} }));

import { listCanaryExecutions } from './canaryRun.service';

describe('listCanaryExecutions', () => {
  beforeEach(() => jest.clearAllMocks());

  it('uses the explicitly provided route count', async () => {
    await listCanaryExecutions('deck', { params: { count: 50 } });

    expect(mockPath).toHaveBeenCalledWith('deck', 'executions');
    expect(mockQuery).toHaveBeenCalledWith({ limit: 50 });
  });

  it('defaults the execution count when the route omits it', async () => {
    await listCanaryExecutions('deck', { params: {} });

    expect(mockQuery).toHaveBeenCalledWith({ limit: 20 });
  });
});
