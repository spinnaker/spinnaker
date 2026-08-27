import { FleetOriginGuard } from './fleetOriginGuard';
import { AuthenticationService } from '../authentication/AuthenticationService';
import type { IFleetSettings } from '../config/settings';
import { SETTINGS } from '../config/settings';

describe('FleetOriginGuard', () => {
  // A host that is definitely not the Karma origin, standing in for the fleet's global URL.
  const OTHER_ORIGIN = 'https://spinnaker.example.com';

  let originalFleet: IFleetSettings | undefined;
  let navigateTo: jasmine.Spy;

  beforeEach(() => {
    originalFleet = SETTINGS.fleet;
    navigateTo = spyOn(FleetOriginGuard, 'navigateTo');
    AuthenticationService.reset();
  });

  afterEach(() => {
    SETTINGS.fleet = originalFleet;
    AuthenticationService.reset();
  });

  function setFleet(fleet: Partial<IFleetSettings> | undefined): void {
    SETTINGS.fleet = fleet as IFleetSettings;
  }

  function signIn(isAdmin: boolean): void {
    AuthenticationService.setAuthenticatedUser({ name: 'alice', authenticated: true, isAdmin });
  }

  describe('when fleet mode is not in play', () => {
    it('does nothing when fleet settings are absent', () => {
      setFleet(undefined);

      expect(FleetOriginGuard.enforce()).toBe(true);
      expect(navigateTo).not.toHaveBeenCalled();
    });

    it('does nothing when fleet mode is disabled', () => {
      setFleet({ enabled: false, globalUrl: OTHER_ORIGIN });

      expect(FleetOriginGuard.enforce()).toBe(true);
      expect(navigateTo).not.toHaveBeenCalled();
    });

    it('does nothing when globalUrl is not configured', () => {
      setFleet({ enabled: true, globalUrl: '' });

      expect(FleetOriginGuard.enforce()).toBe(true);
      expect(navigateTo).not.toHaveBeenCalled();
    });

    it('does not trap the user when globalUrl is unparseable', () => {
      setFleet({ enabled: true, globalUrl: 'http://:::' });

      expect(FleetOriginGuard.enforce()).toBe(true);
      expect(navigateTo).not.toHaveBeenCalled();
    });
  });

  describe('when already on the global URL', () => {
    it('lets a non-admin through', () => {
      setFleet({ enabled: true, globalUrl: window.location.origin });
      signIn(false);

      expect(FleetOriginGuard.enforce()).toBe(true);
      expect(navigateTo).not.toHaveBeenCalled();
    });

    it('tolerates a globalUrl carrying a trailing path, comparing origins only', () => {
      setFleet({ enabled: true, globalUrl: `${window.location.origin}/some/path` });
      signIn(false);

      expect(FleetOriginGuard.enforce()).toBe(true);
      expect(navigateTo).not.toHaveBeenCalled();
    });
  });

  describe('when on an instance URL', () => {
    beforeEach(() => setFleet({ enabled: true, globalUrl: OTHER_ORIGIN }));

    it('redirects a non-admin to the global URL', () => {
      signIn(false);

      expect(FleetOriginGuard.enforce()).toBe(false);
      expect(navigateTo).toHaveBeenCalledTimes(1);
      expect(navigateTo.calls.mostRecent().args[0]).toMatch(`^${OTHER_ORIGIN}`);
    });

    it('preserves the current path, query and hash', () => {
      signIn(false);
      const { pathname, search, hash } = window.location;

      FleetOriginGuard.enforce();

      expect(navigateTo).toHaveBeenCalledWith(`${OTHER_ORIGIN}${pathname}${search}${hash}`);
    });

    it('leaves an admin alone', () => {
      signIn(true);

      expect(FleetOriginGuard.enforce()).toBe(true);
      expect(navigateTo).not.toHaveBeenCalled();
    });

    it('redirects an unauthenticated user, who cannot be an admin', () => {
      expect(FleetOriginGuard.enforce()).toBe(false);
      expect(navigateTo).toHaveBeenCalledTimes(1);
    });
  });
});
