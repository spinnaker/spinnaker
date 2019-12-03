import React from 'react';
import { ReactInjector } from 'core/reactShims';
import { NotFound } from 'core/notfound/NotFound';

export function ExecutionNotFound() {
  const { params } = ReactInjector.$state;
  return <NotFound type="Execution" entityId={params.executionId} />;
}
