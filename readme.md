# A tool to find changed files in a java distro.

## Synopsis 

Does a deep comparison of two versions of a distribution and reports which files differ from the first distribution.
It will reclusively drill into .zip, .jar, and .war files and compare their contents.

It is currently ignoring change in the following file patterns since they tend to always change between two builds
of a maven module:

* `/META-INF/NOTICE`
* `/META-INF/MANIFEST.MF`
* `/META-INF/DEPENDENCIES`
* `/META-INF/maven/*`

## Requires:

* Java 8
* Maven to build

## Usage:

    java -jar target/distrodiff-1.0-SNAPSHOT.jar [options] <distro> <distro>
    
## Options:

    -v       : Verbose mode. Show more details about why files are different.
    -vf name : Only enable verbose mode for file paths ending with the value specified
    -i name  : ignore changed files that match the specified name

## Example: 

    $ java -jar distrodiff-1.0-SNAPSHOT.jar boss-fuse-full-6.1.0.redhat-379.zip jboss-fuse-full-6.1.0.redhat-387.zip 
    jboss-fuse-full-6.1.0.redhat-387.zip!/jboss-fuse-6.1.0.redhat-387/system/io/fabric8/fabric-zookeeper-commands/1.0.0.redhat-387/fabric-zookeeper-commands-1.0.0.redhat-387.jar: different
    jboss-fuse-full-6.1.0.redhat-387.zip!/jboss-fuse-6.1.0.redhat-387/system/io/fabric8/fabric-project-deployer/1.0.0.redhat-387/fabric-project-deployer-1.0.0.redhat-387.jar: different
    jboss-fuse-full-6.1.0.redhat-387.zip!/jboss-fuse-6.1.0.redhat-387/system/io/fabric8/fabric-camel-autotest/1.0.0.redhat-387/fabric-camel-autotest-1.0.0.redhat-387.jar: different
    ...
    jboss-fuse-full-6.1.0.redhat-387.zip!/jboss-fuse-6.1.0.redhat-387/lib/esb-version.jar: different
    jboss-fuse-full-6.1.0.redhat-387.zip!/jboss-fuse-6.1.0.redhat-387/extras/apache-cxf-2.7.0.redhat-610387.zip: different
    jboss-fuse-full-6.1.0.redhat-387.zip!/jboss-fuse-6.1.0.redhat-387/extras/apache-camel-2.12.0.redhat-610387.zip: different

    $ java -jar distrodiff-1.0-SNAPSHOT.jar -vf fabric-zookeeper-commands-1.0.0.redhat-387.jar jboss-fuse-full-6.1.0.redhat-387.zip 
    jboss-fuse-full-6.1.0.redhat-387.zip!/jboss-fuse-6.1.0.redhat-387/system/io/fabric8/fabric-zookeeper-commands/1.0.0.redhat-387/fabric-zookeeper-commands-1.0.0.redhat-387.jar: different
      /io/fabric8/zookeeper/commands/Create.class: A binary difference found at byte offset: 9
