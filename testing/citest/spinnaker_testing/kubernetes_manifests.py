# Copyright 2018 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import citest.json_contract as jc
import citest.json_predicate as jp
import citest.service_testing as st

ov_factory = jc.ObservationPredicateFactory()

"""Utilities for producing and manipulating Kubernetes Manifests stored as
dictionaries"""

class KubernetesManifestFactory(object):
  """Utilities for building Kubernetes Manifests"""

  def __init__(self, scenario):
    self.scenario = scenario

  def service(self, name):
    return {
        'apiVersion': 'v1',
        'kind': 'Service',
        'metadata': {
            'name': name,
            'namespace': self.scenario.TEST_NAMESPACE,
            'labels': {
                'app': self.scenario.TEST_APP,
                'owner': 'citest',
            }
        },
        'spec': {
            'selector': {
                'app': self.scenario.TEST_APP,
            },
            'ports': [{
                'protocol': 'TCP',
                'port': 80
            }]
        }
    }

  def deployment(self, name, image):
    return {
        'apiVersion': 'apps/v1beta2',
        'kind': 'Deployment',
        'metadata': {
            'name': name,
            'namespace': self.scenario.TEST_NAMESPACE,
            'labels': {
                'app': self.scenario.TEST_APP,
                'owner': 'citest',
            }
        },
        'spec': {
            'replicas': 1,
            'selector': {
                'matchLabels': {
                    'app': self.scenario.TEST_APP,
                }
            },
            'template': {
                'metadata': {
                    'labels': {
                        'app': self.scenario.TEST_APP,
                        'owner': 'citest',
                    }
                },
                'spec': {
                    'containers': [{
                        'name': 'primary',
                        'image': image,
                    }]
                }
            }
        }
    }

  def add_configmap_volume(self, deployment, configmap_name):
    deployment['spec']['template']['spec']['volumes'] = [{
      'name': 'test-volume',
      'configMap': {
        'name': configmap_name,
      },
    }]

  def config_map(self, name, data):
    return {
        'apiVersion': 'v1',
        'kind': 'ConfigMap',
        'metadata': {
            'name': name,
            'namespace': self.scenario.TEST_NAMESPACE,
            'labels': {
                'app': self.scenario.TEST_APP,
                'owner': 'citest',
            }
        },
        'data': data
    }

class KubernetesManifestPredicateFactory(object):
  def config_map_key_value_predicate(self, key, value):
    return ov_factory.value_list_contains(jp.DICT_MATCHES({
         'data': jp.DICT_MATCHES({
             key: jp.STR_EQ(value)
         })
     }))

  def deployment_configmap_mounted_predicate(self, configmap_name):
    return ov_factory.value_list_contains(jp.DICT_MATCHES({
         'spec': jp.DICT_MATCHES({
           'template': jp.DICT_MATCHES({
             'spec': jp.DICT_MATCHES({
               'volumes': jp.LIST_MATCHES([jp.DICT_MATCHES({
                  'configMap': jp.DICT_MATCHES({
                    'name': jp.STR_SUBSTR(configmap_name)
                   })
                 })
               ])
             })
           })
         }),
         'status': jp.DICT_MATCHES({
           'availableReplicas': jp.NUM_GE(1)
         })
     }))

  def service_selector_predicate(self, key, value):
    return ov_factory.value_list_contains(jp.DICT_MATCHES({
         'spec': jp.DICT_MATCHES({
           'selector': jp.DICT_MATCHES({
             key: jp.STR_EQ(value)
           })
         }),
     }))

  def deployment_image_predicate(self, image):
    return ov_factory.value_list_contains(jp.DICT_MATCHES({
         'spec': jp.DICT_MATCHES({
           'template': jp.DICT_MATCHES({
             'spec': jp.DICT_MATCHES({
               'containers': jp.LIST_MATCHES([
                 jp.DICT_MATCHES({ 'image': jp.STR_EQ(image) })
               ])
             })
           })
         }),
         'status': jp.DICT_MATCHES({
           'availableReplicas': jp.NUM_GE(1)
         })
     }))

  def not_found_observation_predicate(self, title='Not Found Permitted'):
    return ov_factory.error_list_contains(st.CliAgentRunErrorPredicate(
      title=title, error_regex='.* not found'))
