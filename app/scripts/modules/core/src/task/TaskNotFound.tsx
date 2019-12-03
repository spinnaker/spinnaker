import React from 'react';
import { ReactInjector } from 'core/reactShims';
import { NotFound } from 'core/notfound/NotFound';

export function TaskNotFound() {
  const { params } = ReactInjector.$state;
  return <NotFound type="Task" entityId={params.taskId} />;
}
