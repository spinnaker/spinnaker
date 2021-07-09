import React from 'react';

import { NotFound } from '../../notfound/NotFound';
import { ReactInjector } from '../../reactShims';

export function ExecutionNotFound() {
  const { params } = ReactInjector.$state;
  return <NotFound type="Execution" entityId={params.executionId} />;
}
