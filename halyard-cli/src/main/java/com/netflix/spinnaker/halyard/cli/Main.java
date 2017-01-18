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

    HalCommand hal = new HalCommand();
    JCommander jc = new JCommander(hal);
    hal.setCommander(jc).configureSubcommands();

    try {
      jc.parse(args);
    } catch (ParameterException e) {
      System.out.println(e.getMessage());
      System.exit(1);
    }

    try {
      hal.execute();
    } catch (IllegalArgumentException e) {
      AnsiUi.error("Illegal argument: " + e.getMessage());
      System.exit(1);
    } catch (Exception e) {
      if (globalOptions.isDebug()) {
        e.printStackTrace();
      }

      AnsiUi.error(e.getMessage());
      AnsiUi.remediation("That wasn't supposed to happen.\nPlease report an issue on https://github.com/spinnaker/halyard/issues");
      System.exit(1);
    }
  }
}
