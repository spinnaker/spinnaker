import { Clouddriver } from './clouddriver';
import { Deck } from './deck';
import { Echo } from './echo';
import { Fiat } from './fiat';
import { Front50 } from './front50';
import { Gate } from './gate';
import { Igor } from './igor';
import { Kayenta } from './kayenta';
import { MonitoringDaemon } from './monitoring_daemon';
import { MonitoringThirdParty } from './monitoring_third_party';
import { Orca } from './orca';
import { Rosco } from './rosco';
import { Service } from '../service';

export const services: Service[] = [
  new Clouddriver(),
  new Deck(),
  new Echo(),
  new Fiat(),
  new Front50(),
  new Gate(),
  new Igor(),
  new Kayenta(),
  new MonitoringDaemon({
    commit: '96d510cb22f65dcf788324ed8b68447c31de255a',
    version: '1.4.0',
  }),
  new MonitoringThirdParty({
    commit: '96d510cb22f65dcf788324ed8b68447c31de255a',
    version: '1.4.0',
  }),
  new Orca(),
  new Rosco(),
];
