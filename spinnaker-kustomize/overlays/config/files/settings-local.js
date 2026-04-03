// Overrides for Deck default configuration:
// https://github.com/spinnaker/deck/blob/master/packages/app/src/settings.js

// Some basic settings examples.
window.spinnakerSettings.feature.kubernetesRawResources = false;
window.spinnakerSettings.feature.kustomizeEnabled = true;
window.spinnakerSettings.feature.artifactsRewrite = true;
window.spinnakerSettings.feature.functions = false;
window.spinnakerSettings.kubernetesAdHocInfraWritesEnabled = true;
window.spinnakerSettings.authEnabled = true;

// service accounts are automatically created as needed.
window.spinnakerSettings.feature.managedServiceAccounts = true;
