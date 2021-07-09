import React from 'react';

import { NotFound } from '../notfound/NotFound';
import { ReactInjector } from '../reactShims';

export function TaskNotFound() {
  const { params } = ReactInjector.$state;
  return <NotFound type="Task" entityId={params.taskId} />;
}
