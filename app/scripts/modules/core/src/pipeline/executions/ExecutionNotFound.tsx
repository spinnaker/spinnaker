import React from 'react';

import { NotFound } from 'core/notfound/NotFound';
import { ReactInjector } from 'core/reactShims';

export function ExecutionNotFound() {
  const { params } = ReactInjector.$state;
  return <NotFound type="Execution" entityId={params.executionId} />;
}
