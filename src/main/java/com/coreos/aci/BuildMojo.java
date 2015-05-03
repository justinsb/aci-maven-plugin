package com.coreos.aci;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Used to build ACI images.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class BuildMojo extends BaseAciMojo {

  @Override
  public void execute() throws MojoExecutionException {
    validateParameters();

    buildAci();
  }

}
