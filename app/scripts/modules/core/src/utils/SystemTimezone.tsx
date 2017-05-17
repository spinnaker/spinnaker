import * as React from 'react';
import * as moment from 'moment';

import { SETTINGS } from 'core/config/settings';

export class SystemTimezone extends React.Component<any, any> {
  public render() {
    const zone = SETTINGS.defaultTimeZone;
    return (<span>{moment.tz(Date.now(), zone).zoneAbbr()}</span>);
  }
}
