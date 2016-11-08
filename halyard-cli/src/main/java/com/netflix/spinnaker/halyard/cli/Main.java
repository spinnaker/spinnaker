package com.netflix.spinnaker.halyard.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class Main {
  @Parameter(names = "--param1", description = "my param")
  private String param1;

  public static void main(String[] args) {
    Main ep = new Main();
    new JCommander(ep, args);
    System.out.println("hi  ~~" + ep.param1 + "~~");
  }
}
