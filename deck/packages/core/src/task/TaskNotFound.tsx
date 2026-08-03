import { useCurrentStateAndParams } from '@uirouter/react';
import React from 'react';

import { NotFound } from '../notfound/NotFound';

export function TaskNotFound() {
  const { params } = useCurrentStateAndParams();
  return <NotFound type="Task" entityId={params.taskId} />;
}
