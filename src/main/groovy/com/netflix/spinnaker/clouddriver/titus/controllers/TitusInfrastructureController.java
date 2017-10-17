package com.netflix.spinnaker.clouddriver.titus.controllers;

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/titus")
public class TitusInfrastructureController {

  @Autowired
  TitusClientProvider titusClientProvider;

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;

  @RequestMapping(value = "/job/{account}/{region}/{jobId}", method = RequestMethod.GET)
  Object getJobDetails(@PathVariable("account") String account, @PathVariable("region") String region, @PathVariable("jobId") String jobId) {
    return titusClientProvider.getTitusClient((NetflixTitusCredentials) accountCredentialsProvider.getCredentials(account), region).getJobJson(jobId);
  }

  @RequestMapping(value = "/task/{account}/{region}/{taskId}", method = RequestMethod.GET)
  Object getTaskDetails(@PathVariable("account") String account, @PathVariable("region") String region, @PathVariable("taskId") String taskId) {
    return titusClientProvider.getTitusClient((NetflixTitusCredentials) accountCredentialsProvider.getCredentials(account), region).getTaskJson(taskId);
  }

}
