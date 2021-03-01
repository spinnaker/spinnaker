export const viewConfigurationByEventType = {
  ResourceCreated: {
    displayName: 'Created',
    iconClass: 'icon-md-created',
    level: 'info',
  },
  ResourceUpdated: {
    displayName: 'Config updated',
    // Needs it's own icon
    iconClass: 'icon-md-created',
    level: 'info',
  },
  ResourceDeleted: {
    displayName: 'Deleted',
    // Needs it's own icon
    iconClass: 'icon-md-missing-resource',
    level: 'info',
  },
  ResourceMissing: {
    displayName: 'Missing',
    iconClass: 'icon-md-missing-resource',
    level: 'info',
  },
  ResourceValid: {
    displayName: 'Valid',
    // Needs it's own icon
    iconClass: 'icon-md-delta-resolved',
    level: 'info',
  },
  ResourceDeltaDetected: {
    displayName: 'Difference detected',
    iconClass: 'icon-md-delta-detected',
    level: 'info',
  },
  ResourceDeltaResolved: {
    displayName: 'Difference resolved',
    iconClass: 'icon-md-delta-resolved',
    level: 'info',
  },
  ResourceActuationLaunched: {
    displayName: 'Task launched',
    iconClass: 'icon-md-actuation-launched',
    level: 'info',
  },
  ResourceCheckError: {
    displayName: 'Error',
    // Needs it's own icon
    iconClass: 'icon-md-error',
    level: 'error',
  },
  ResourceCheckUnresolvable: {
    displayName: 'Temporary issue',
    // Needs it's own icon, but could likely be same as ResourceCheckError
    iconClass: 'icon-md-error',
    level: 'warning',
  },
  ResourceActuationPaused: {
    displayName: 'Management paused',
    // Needs it's own icon
    iconClass: 'icon-md-paused',
    level: 'warning',
  },
  ResourceActuationResumed: {
    displayName: 'Management resumed',
    // Needs it's own icon
    iconClass: 'icon-md-resumed',
    level: 'info',
  },
} as const;
