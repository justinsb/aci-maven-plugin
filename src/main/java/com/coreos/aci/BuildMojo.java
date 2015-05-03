package com.coreos.aci;

import com.coreos.appc.AppcContainerBuilder;
import com.coreos.appc.ContainerBuilder;
import com.coreos.appc.ContainerFile;
import com.google.common.base.Strings;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Used to build ACI images.
 */
@Mojo(name = "build")
public class BuildMojo extends BaseAciMojo {

  /** The base image to use. */
  @Parameter(property = "baseImage")
  private String baseImage;

  /** The created image will be given this name. */
  @Parameter(property = "imageName")
  private String imageName;

  /** The cmd command for the image. */
  @Parameter(property = "cmd")
  private String cmd;

  @Override
  public void execute() throws MojoExecutionException {
    // final File workdir = new File(projectBuildDirectory, "aci");

    validateParameters();

    File imageFile = new File(projectBuildDirectory, "image.aci");
    ContainerBuilder builder = new AppcContainerBuilder(imageFile);
    builder.log = getSlf4jLogger();
    builder.baseImage = baseImage;
    builder.cmd = cmd;
    // builder.env = env;
    // builder.entryPoint = entryPoint;
    // builder.exposesSet = exposesSet;
    // builder.maintainer = maintainer;

    try {
      copyArtifacts(builder);
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to copy artifacts", e);
    }

    // copyResources(builder);

    String imageVersion = mavenProject.getVersion();

    try {
      builder.buildImage(imageName, imageVersion);
    } catch (Exception e) {
      throw new MojoExecutionException("Failed to build image", e);
    }

    // if (pushImage) {
    // if (builder instanceof DockerContainerBuilder) {
    // pushImage(docker, imageName, getLog());
    // } else if (builder instanceof AppcContainerBuilder) {
    // AciRepository aciRepository = getAciRepository();
    // AciImageInfo imageInfo = new AciImageInfo();
    // imageInfo.name = imageName;
    // imageInfo.version = imageVersion;
    //
    // byte[] signature = null;
    // if (signImage) {
    // AciSigner signer = new GpgCommandAciSigner();
    // signature = signer.sign(imageFile);
    // }
    //
    // aciRepository.push(imageInfo, imageFile, signature, getSlf4jLogger());
    // } else {
    // throw new IllegalStateException();
    // }
    // }
  }

  private void validateParameters() throws MojoExecutionException {
    BuildType buildType = detectBuildType();

    if (Strings.isNullOrEmpty(baseImage)) {
      if (buildType != null) {
        baseImage = buildType.getDefaultBaseImage();
      } else {
        throw new MojoExecutionException("Must specify baseImage");
      }
    }

    if (Strings.isNullOrEmpty(imageName)) {
      throw new MojoExecutionException("Must specify imageName");
    }

    if (Strings.isNullOrEmpty(cmd)) {
      throw new MojoExecutionException("Must specify cmd");
    }
  }

  private void copyArtifacts(ContainerBuilder builder) throws IOException, MojoExecutionException {
    List<ContainerFile> containerFiles = new ArrayList<>();

    Artifact mainArtifact = mavenProject.getArtifact();
    if (mainArtifact == null) {
      throw new MojoExecutionException("Could not find primary artifact");
    }
    File file = mainArtifact.getFile();
    if (file == null) {
      throw new MojoExecutionException("No file found for primary artifact (" + mainArtifact + ")");
    }
    String targetName = file.getName();

    boolean copyDependencies = false;
    BuildType buildType = detectBuildType();
    if (buildType == null) {
      throw new MojoExecutionException("Cannot automatically copy artifacts; build type unknown");
    }

    switch (buildType) {
    case WAR:
      targetName = "root.war";
      break;

    case JAR:
      targetName = "app.jar";
      copyDependencies = true;
      break;

    default:
      throw new MojoExecutionException("Unknown build type: " + buildType);
    }

    String imagePath = "/app/" + targetName;

    ContainerFile containerFile = new ContainerFile(file.toPath(), imagePath);
    getLog().debug("Copying " + file + " to " + imagePath);
    containerFiles.add(containerFile);

    if (copyDependencies) {
      List<Artifact> runtimeDependencies = getDependencyArtifacts();
      for (Artifact runtimeDependency : runtimeDependencies) {
        file = runtimeDependency.getFile();
        imagePath = "/app/lib/" + file.getName();
        containerFile = new ContainerFile(file.toPath(), imagePath);
        containerFiles.add(containerFile);
      }
    }

    builder.addFiles(containerFiles);
  }

}
