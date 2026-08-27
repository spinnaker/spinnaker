import { AuthenticationService } from '../authentication/AuthenticationService';
import { SETTINGS } from '../config/settings';

/**
 * Keeps ordinary users on a Spinnaker fleet's global URL.
 *
 * In a fleet deployment several Spinnaker instances sit behind a single global URL, and an edge
 * router assigns each user to an instance. Users should only ever see the global URL, but the
 * per-instance SAML Assertion Consumer Service means a browser does briefly visit an instance
 * hostname during login, and instance URLs can also leak by being shared. If a non-admin ends up
 * loading Deck from an instance origin, send them back to the global URL so the mask self-corrects
 * rather than eroding.
 *
 * Admins are deliberately left alone, so they can work against a specific instance directly.
 *
 * See gate/docs/fleet.md.
 */
export class FleetOriginGuard {
  /**
   * Seam for the actual navigation, so tests can assert the computed target without the browser
   * leaving the page.
   */
  public static navigateTo(url: string): void {
    window.location.href = url;
  }

  /**
   * Must be called after authentication has resolved: `isAdmin` is only populated once
   * `/auth/user` has returned.
   *
   * @returns `true` to continue booting Deck, `false` when a redirect has been initiated and the
   * caller should stop.
   */
  public static enforce(): boolean {
    const fleet = SETTINGS.fleet;
    if (!fleet?.enabled || !fleet.globalUrl) {
      return true;
    }

    let globalOrigin: string;
    try {
      globalOrigin = new URL(fleet.globalUrl, window.location.origin).origin;
    } catch (error) {
      console.error(`Invalid fleet.globalUrl setting: ${fleet.globalUrl}`, error);
      return true;
    }

    if (window.location.origin === globalOrigin) {
      return true;
    }

    if (AuthenticationService.getAuthenticatedUser().isAdmin) {
      return true;
    }

    // Preserve wherever the user was trying to go.
    const { pathname, search, hash } = window.location;
    this.navigateTo(`${globalOrigin}${pathname}${search}${hash}`);
    return false;
  }
}
