import { closeServerGroupManagerDetails } from './ServerGroupManagerDetails';

describe('closeServerGroupManagerDetails', () => {
  it('replaces details through the injected state service', () => {
    const stateService = { go: jasmine.createSpy('go'), params: {} };

    closeServerGroupManagerDetails(stateService as any);

    expect(stateService.params.allowModalToStayOpen).toBe(true);
    expect(stateService.go).toHaveBeenCalledWith('^', null, { location: 'replace' });
  });
});
