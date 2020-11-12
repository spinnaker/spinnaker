package com.netflix.spinnaker.keel.api

interface ScmInfo{

  /**
   * This is a bridge to calling Igor in order to get all configured SCM base URLs.
   */
  suspend fun getScmInfo():
    Map<String, String?>
}
