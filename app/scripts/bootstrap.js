import { bootstrap, element } from 'angular';

element(document.documentElement).ready(() => {
  bootstrap(document.documentElement, ['netflix.spinnaker']);
});
