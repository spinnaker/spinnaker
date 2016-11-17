package com.netflix.spinnaker.halyard.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.netflix.spinnaker.halyard.cli.command.v1.GlobalOptions;
import com.netflix.spinnaker.halyard.cli.command.v1.HalCommand;
import com.netflix.spinnaker.halyard.cli.ui.v1.AnsiUi;
import retrofit.RetrofitError;

public class Main {
  public static void main(String[] args) {
    GlobalOptions globalOptions = GlobalOptions.getGlobalOptions();
    JCommander jc = new JCommander(globalOptions);

    HalCommand hal = new HalCommand(jc);

    try {
      jc.parse(args);
    } catch (ParameterException e) {
      System.out.println(e.getMessage());
      jc.usage();
    }

    try {
      hal.execute();
    } catch (RetrofitError e) {
      AnsiUi.error(e.getMessage());
    }
  }
}
