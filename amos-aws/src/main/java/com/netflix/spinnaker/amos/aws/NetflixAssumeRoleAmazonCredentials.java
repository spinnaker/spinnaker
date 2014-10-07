/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.amos.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Dan Woods
 * @see com.netflix.spinnaker.amos.aws.AssumeRoleAmazonCredentials
 */
public class NetflixAssumeRoleAmazonCredentials extends NetflixAmazonCredentials {
    private final AtomicReference<STSAssumeRoleSessionCredentialsProvider> stsSessionCredentialsProvider = new AtomicReference<>(null);
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * The role to assume on the target account.
     */
    private String assumeRole;
    private String sessionName = "Spinnaker";

    public String getAssumeRole() {
        return assumeRole;
    }

    public void setAssumeRole(String assumeRole) {
        this.assumeRole = assumeRole;
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    @Override
    public AWSCredentials getCredentials() {
        if (stsSessionCredentialsProvider.get() == null) {
            lock.lock();
            try {
                if (stsSessionCredentialsProvider.get() == null) {
                    this.stsSessionCredentialsProvider.set(new STSAssumeRoleSessionCredentialsProvider(credentialsProvider,
                            String.format("arn:aws:iam::%s:%s", getAccountId(), assumeRole), sessionName));
                }
            } finally {
                lock.unlock();
            }
        }

        return this.stsSessionCredentialsProvider.get().getCredentials();
    }
}
