package com.coreos.aci;

import java.io.File;
import java.net.URI;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3Client;
import com.coreos.appc.AciRepository;
import com.coreos.appc.S3AciRepository;
import com.google.common.base.Strings;

public abstract class BaseAciMojo extends AbstractMojo {

  @Parameter(property = "project.build.directory")
  protected File projectBuildDirectory;

  /**
   * The ACI repository to push to. Must be set for a push.
   */
  @Parameter(property = "repository")
  protected String aciRepository;

  protected AciRepository getAciRepository() throws MojoExecutionException {
    if (Strings.isNullOrEmpty(this.aciRepository)) {
      throw new MojoExecutionException("Must set repository to push to");
    }
    URI uri = URI.create(this.aciRepository);
    String scheme = uri.getScheme();
    if (scheme.equals("s3")) {
      AWSCredentialsProvider provider = new DefaultAWSCredentialsProviderChain();
      AmazonS3Client amazonS3Client = new AmazonS3Client(provider);
      String bucketName = uri.getHost();
      String prefix = uri.getPath();
      return new S3AciRepository(amazonS3Client, bucketName, prefix);
    } else {
      throw new MojoExecutionException("Unknown repository scheme: " + scheme);
    }
  }

}
