package com.netflix.spinnaker.orca.echo

import retrofit.client.Response

interface EchoEventPoller {

  Response getEvents(String type)

}