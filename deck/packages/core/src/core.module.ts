/* eslint-disable @spinnaker/import-sort */
import 'bootstrap/dist/css/bootstrap.css';
import 'bootstrap/dist/js/bootstrap';

import '@fortawesome/fontawesome-free/css/solid.css';
import '@fortawesome/fontawesome-free/css/regular.css';
import '@fortawesome/fontawesome-free/css/fontawesome.css';
import 'react-select/dist/react-select.css';
import 'react-virtualized/styles.css';
import 'react-virtualized-select/styles.css';
import 'ui-select/dist/select.css';
import '@spinnaker/styleguide/public/styleguide.min.css';
import 'select2/select2.css';
import 'select2-bootstrap-css/select2-bootstrap.css';
import 'source-sans/source-sans-3.css';
import './fonts/icons.css';

import './navigation/legacyStateConfig.bridge';

import './analytics/GoogleAnalyticsInitializer';
import './apitoken/apitoken.module';
import './application/application.module';
import './artifact/artifact.module';
import './authentication/authentication.module';
import './banner/global/globalbanner.module';
import './ci/ci.module';
import './cloudProvider/cloudProvider.module';
import './cluster/cluster.module';
import './deploymentStrategy/deploymentStrategy.module';
import './diffs';
import './entityTag/entityTags.module';
import './forms/forms.module';
import './function/function.module';
import './healthCounts/healthCounts.module';
import './help/help.module';
import './insight/insight.module';
import './instance/instance.module';
import './interceptor/interceptor.module';
import './loadBalancer/loadBalancer.module';
import './managed';
import './modal/modal.module';
import './notification/notifications.module';
import './pageTitle/pageTitle.module';
import './pagerDuty/pagerDuty.module';
import './pipeline/pipeline.module';
import './pipeline/config/templates/pipelineTemplate.module';
import './plugins';
import './projects/projects.module';
import './reactShims';
import './region/region.module';
import './search/search.module';
import './securityGroup/securityGroup.module';
import './serverGroup/serverGroup.module';
import './serverGroupManager/serverGroupManager.module';
import './slack';
import './state';
import './styleguide/styleguide.module';
import './subnet/subnet.module';
import './task/task.module';
import './utils/utils.module';
import './validation/validation.module';
import './widgets/widgets.module';

import { initGoogleAnalytics } from './reactShims/react.ga';

initGoogleAnalytics();

export { bootstrapDeck } from './bootstrap/bootstrapDeck';
