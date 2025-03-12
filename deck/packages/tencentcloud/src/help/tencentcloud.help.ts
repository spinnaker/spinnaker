import { HelpContentsRegistry } from '@spinnaker/core';

const helpContents: { [key: string]: string } = {};

Object.keys(helpContents).forEach((key) => HelpContentsRegistry.register(key, helpContents[key]));
