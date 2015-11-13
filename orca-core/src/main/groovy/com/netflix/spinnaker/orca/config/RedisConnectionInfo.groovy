package com.netflix.spinnaker.orca.config

import redis.clients.jedis.Protocol
import redis.clients.util.JedisURIHelper

class RedisConnectionInfo {

  String host
  int port
  int database
  String password

  boolean hasPassword() {
    password?.length() > 0
  }

  static RedisConnectionInfo parseConnectionUri(String connection) {

    URI redisConnection = URI.create(connection)

    String host = redisConnection.host
    int port = redisConnection.port == -1 ? Protocol.DEFAULT_PORT : redisConnection.port

    int database = JedisURIHelper.getDBIndex(redisConnection)

    String password = JedisURIHelper.getPassword(redisConnection)

    new RedisConnectionInfo([
        host: host,
        port: port,
        database: database,
        password: password
    ])
  }

}
