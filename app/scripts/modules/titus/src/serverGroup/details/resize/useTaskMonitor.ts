import { useMemo } from 'react';
import { ITaskMonitorConfig, TaskMonitor } from '@spinnaker/core';

/**
 * React hook that returns a TaskMonitor
 *
 * @param config a ITaskMonitorConfig
 * @param dismissModal a function that closes the modal enclosing the task monitor
 *
 * Example:
 *
 * function MyComponent(props) {
 *   const { application, serverGroup } = props;
 *   const title = `Resize ${serverGroup.name}`;
 *   const taskMonitor = useTaskMonitor({ application, title });
 *
 *   return (
 *     <>
 *       <TaskMonitorWrapper taskMonitor={taskMonitor}>
 *       <form onSubmit={() => taskMonitor.submit(() => API.runSomeTask())}>
 *     </>
 *   )
 * }
 *
 */
export const useTaskMonitor = (config: ITaskMonitorConfig, dismissModal: () => void) => {
  const modalInstance = TaskMonitor.modalInstanceEmulation(() => dismissModal());
  return useMemo(() => new TaskMonitor({ modalInstance, ...config }), [config.application, config.title]);
};
