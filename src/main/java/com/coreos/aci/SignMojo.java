package com.coreos.aci;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import java.io.File;

/**
 * Used to build & sign ACI images.
 */
@Mojo(name = "sign")
public class SignMojo extends BaseAciMojo {

  @Override
  public void execute() throws MojoExecutionException {
    validateParameters();

    File imageFile = buildAci();
    signAci(imageFile);
  }
}
