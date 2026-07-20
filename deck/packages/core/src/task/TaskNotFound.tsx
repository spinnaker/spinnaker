import React from 'react';
import { AngularServices } from '../angular/services';

import { NotFound } from '../notfound/NotFound';

export function TaskNotFound() {
  const { params } = AngularServices.$state;
  return <NotFound type="Task" entityId={params.taskId} />;
}
