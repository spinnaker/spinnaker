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

"""Utilities for producing and running pipelines in test cases"""

class PipelineSupport(object):
  def __init__(self, scenario):
    self.scenario = scenario

  def submit_pipeline_contract(self, name, stages, expectedArtifacts=[], user='anonymous'):
    s = self.scenario
    job = {
        'keepWaitingPipelines': 'false',
        'application': s.TEST_APP,
        'name': name,
        'lastModifiedBy': user,
        'limitConcurrent': 'true',
        'parallel': 'true',
        'stages': stages,
        'expectedArtifacts': expectedArtifacts,
    }
    payload = s.agent.make_json_payload_from_kwargs(**job)
    expect_match = {key: jp.EQUIVALENT(value)
                    for key, value in job.items()}
    expect_match['stages'] = jp.LIST_MATCHES(
        [jp.DICT_MATCHES({key: jp.EQUIVALENT(value)
                         for key, value in stage.items()}) for stage in stages])

    builder = st.HttpContractBuilder(s.agent)
    (builder.new_clause_builder('Has Pipeline',
                                retryable_for_secs=15)
     .get_url_path(
        'applications/{app}/pipelineConfigs'.format(app=s.TEST_APP))
     .contains_match(expect_match))
    return st.OperationContract(
        s.new_post_operation(title='save_pipeline_operation',
                                data=payload, path='pipelines',
                                status_class=st.SynchronousHttpOperationStatus),
        contract=builder.build())
