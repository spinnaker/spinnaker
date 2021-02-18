import { NotFound } from 'core/notfound/NotFound';
import { ReactInjector } from 'core/reactShims';
import React from 'react';

export function TaskNotFound() {
  const { params } = ReactInjector.$state;
  return <NotFound type="Task" entityId={params.taskId} />;
}
