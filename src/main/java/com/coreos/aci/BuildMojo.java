package com.coreos.aci;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Used to build ACI images.
 */
@Mojo(name = "build")
public class BuildMojo extends BaseAciMojo {

  @Override
  public void execute() throws MojoExecutionException {
    validateParameters();

    buildAci();
  }

}
