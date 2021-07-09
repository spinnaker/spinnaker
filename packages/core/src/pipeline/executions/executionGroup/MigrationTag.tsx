import * as React from 'react';

import { Overridable } from '../../../overrideRegistry';

@Overridable('core.executions.migrationTag')
export class MigrationTag extends React.Component<{}, {}> {
  public render() {
    return <span className="migration-tag sp-margin-s-left">MIGRATED</span>;
  }
}
