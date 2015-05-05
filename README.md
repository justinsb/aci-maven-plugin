## Maven plugin for building ACIs (appc images)

This maven plugin makes it easy to produce ACIs (appc images) directly from your maven projects.

### Building the project

To build and run from source:

1. First check-out [appc-java](https://github.com/justinsb/appc-java) and `mvn install` 
1. Check-out this repository and `mvn install`


### Configuring mvn so we can use 'aci'

You can configure `com.coreos` as a well-known plugin prefix.  Add this to `~/.m2/settings.xml`

```
<pluginGroups>
  <pluginGroup>com.coreos</pluginGroup>
</pluginGroups>
``

### Building examples

Then you can try one of the examples:

```
cd examples/example-console1/
mvn package aci:build
```

That produces an ACI in target/image.aci.  

You can run the image:

```
# Trust my signing key, for the Java base images
sudo rkt trust --prefix aci.justinsb.com https://s3-us-west-1.amazonaws.com/aci.justinsb.com/pubkeys.gpg
sudo rkt --insecure-skip-verify run target/image.aci
```

If you have set up GPG with your signing key, you can automatically produce a signed image:

```
cd examples/example-console1/
mvn package aci:sign
sudo rkt run target/image.aci
```


If you have an ACI repo running on S3, you can automatically push the signed image to that repo.
Note that you won't have access to my repo (`aci.justinsb.com`), so you'll want to create your own
first!

```
cd examples/example-console1/
mvn package aci:push
sudo rkt run aci.justinsb.com/example-console1
```

There are 3 examples provided:

* [example-console1](examples/example-console1) is a trivial Java console app
* [example-console2](examples/example-console2) is a similarly trivial app, but it demonstrates that multiple JARs "just work"
* [example-webapp1](examples/example-webapp1) is a simple webapp, which is automatically run with Jetty


For most apps, the plugin does not need any settings, so you can just use `mvn package aci:build`,
`mvn package aci:sign` or `mvn package aci:push`.  But if you want non-default options, consult the
[mvn plugin options](docs/options.md).