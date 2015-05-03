package com.coreos.aci;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.google.common.base.Strings;

import java.io.File;

/**
 * Used to build & push ACI images.
 */
@Mojo(name = "push", defaultPhase = LifecyclePhase.DEPLOY, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class PushMojo extends BaseAciMojo {

  @Override
  public void execute() throws MojoExecutionException {
    validateParameters();

    File imageFile = buildAci();
    byte[] signature = signAci(imageFile);
    pushAci(imageFile, signature);
  }

  protected void validateParameters() throws MojoExecutionException {
    super.validateParameters();

    if (Strings.isNullOrEmpty(aciRepository)) {
      Artifact mainArtifact = getMainArtifact();
      aciRepository = "s3://" + mainArtifact.getGroupId();
      getLog().warn("Defaulting aci repository to " + aciRepository);
    }
  }
}
