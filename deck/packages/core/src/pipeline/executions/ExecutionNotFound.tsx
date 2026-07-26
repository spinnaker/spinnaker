import { useCurrentStateAndParams } from '@uirouter/react';
import React from 'react';

import { NotFound } from '../../notfound/NotFound';

export function ExecutionNotFound() {
  const { params } = useCurrentStateAndParams();
  return <NotFound type="Execution" entityId={params.executionId} />;
}
