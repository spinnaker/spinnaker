/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.deploy

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.clouddriver.cf.utils.CloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
class CloudFoundryServerGroupNameResolver extends AbstractServerGroupNameResolver {

	private static final String PHASE = "DEPLOY"

	private final CloudFoundryAccountCredentials credentials
	private final CloudFoundryClientFactory clientFactory


	CloudFoundryServerGroupNameResolver(CloudFoundryAccountCredentials credentials,
										CloudFoundryClientFactory clientFactory) {
		this.credentials = credentials
		this.clientFactory = clientFactory
	}

	@Override
	String getPhase() {
		PHASE
	}

	@Override
	String getRegion() {
		credentials.org
	}

	@Override
	List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
		clientFactory.createCloudFoundryClient(credentials, true).applications
			.collect { app -> [names: Names.parseName(app.name), app: app]}
			.findAll { data -> data.names.cluster == clusterName }
			.collect { data ->
				new AbstractServerGroupNameResolver.TakenSlot(
					serverGroupName: combineAppStackDetail(data.names.app, data.names.stack, data.names.detail),
					sequence: data.names.sequence,
					createdTime: data.app?.meta?.updated
				)
			}
	}
}
