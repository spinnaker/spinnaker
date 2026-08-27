import * as React from 'react';

import { SETTINGS } from '../config/settings';

/**
 * Shows which fleet instance served this Deck, in the nav bar next to the user menu.
 *
 * Purely informational -- it does not enforce anything (see FleetOriginGuard for that). Renders
 * nothing unless window.spinnakerSettings.fleet.instanceId is set, so a non-fleet deployment is
 * unaffected. See gate/docs/fleet.md.
 */
export const FleetInstanceBadge = () => {
  const instanceId = SETTINGS.fleet?.instanceId;
  if (!instanceId) {
    return null;
  }

  return (
    <li className="fleet-instance-badge">
      <span className="label label-default sp-margin-m-right" title="Spinnaker fleet instance">
        {instanceId}
      </span>
    </li>
  );
};
