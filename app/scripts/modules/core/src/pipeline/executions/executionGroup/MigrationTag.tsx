import { Overridable } from 'core/overrideRegistry';
import * as React from 'react';

@Overridable('core.executions.migrationTag')
export class MigrationTag extends React.Component<{}, {}> {
  public render() {
    return <span className="migration-tag sp-margin-s-left">MIGRATED</span>;
  }
}
