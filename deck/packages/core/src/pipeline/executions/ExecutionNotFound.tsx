import React from 'react';
import { AngularServices } from '../../angular/services';

import { NotFound } from '../../notfound/NotFound';

export function ExecutionNotFound() {
  const { params } = AngularServices.$state;
  return <NotFound type="Execution" entityId={params.executionId} />;
}
