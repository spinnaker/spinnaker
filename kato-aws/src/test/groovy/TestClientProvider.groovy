import com.amazonaws.auth.BasicAWSCredentials
import com.netflix.asgard.kato.security.aws.AmazonClientProvider
import com.netflix.asgard.kato.security.aws.AmazonCredentials

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

def clientProvider = new AmazonClientProvider(edda: "http://entrypoints-v2.%s.%s.netflix.net:7001")
clientProvider.init()
def creds = new AmazonCredentials(new BasicAWSCredentials("foo", "bar"), "test")
def region = "us-west-1"
def ec2 = clientProvider.getAmazonEC2(creds, region)
def as2 = clientProvider.getAutoScaling(creds, region)

println "1: " + ec2.describeSubnets()
println "3: " + ec2.describeSecurityGroups()
println "4: " + as2.describeLaunchConfigurations()
println "5: " + as2.describeAutoScalingGroups()