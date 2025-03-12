/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.aws.bastion;

import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.agentproxy.AgentProxyException;
import com.jcraft.jsch.agentproxy.ConnectorFactory;
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RemoteCredentialsSupport {
  private static final JSch jsch = new JSch();
  private static final Logger log = LoggerFactory.getLogger(RemoteCredentialsSupport.class);
  private static final IdentityRepository identityRepository;
  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    IdentityRepository ir = null;

    try {
      ir = new RemoteIdentityRepository(ConnectorFactory.getDefault().createConnector());
    } catch (AgentProxyException e) {
      log.error("Error setting up default remote identity connector", e);
    }

    identityRepository = ir;
    jsch.setIdentityRepository(identityRepository);

    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
  }

  static RemoteCredentials getRemoteCredentials(
      String command, String user, String host, int port) {

    RemoteCredentials remoteCredentials = new RemoteCredentials();

    try {
      Session session = jsch.getSession(user, host, port);
      Properties config = new Properties();
      config.put("StrictHostKeyChecking", "no");
      config.put("PreferredAuthentications", "publickey");
      config.put("HashKnownHosts", "yes");

      session.setConfig(config);
      session.setPassword("");
      session.connect();

      ChannelExec channel = (ChannelExec) session.openChannel("exec");
      InputStream is = channel.getInputStream();

      channel.setCommand(command);
      channel.connect();

      String output = IOUtils.toString(is);
      log.debug("Remote credentials: {}", output);

      channel.disconnect();
      session.disconnect();

      output = output.replace("\n", "");
      remoteCredentials = objectMapper.readValue(output, RemoteCredentials.class);
    } catch (Exception e) {
      log.error("Remote SSH execution failed.", e);
    }

    return remoteCredentials;
  }

  static class RemoteCredentials {
    private String accessKeyId;
    private String secretAccessKey;
    private String token;
    private String expiration;

    String getAccessKeyId() {
      return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
      this.accessKeyId = accessKeyId;
    }

    String getSecretAccessKey() {
      return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
      this.secretAccessKey = secretAccessKey;
    }

    String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
    }

    String getExpiration() {
      return expiration;
    }

    public void setExpiration(String expiration) {
      this.expiration = expiration;
    }
  }
}
