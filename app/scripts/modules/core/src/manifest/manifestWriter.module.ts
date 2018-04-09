import { module } from 'angular';
import { MANIFEST_WRITER } from 'core/manifest/manifestWriter.service';

export const MANIFEST_MODULE = 'spinnaker.core.manifest';
module(MANIFEST_MODULE, [MANIFEST_WRITER]);
