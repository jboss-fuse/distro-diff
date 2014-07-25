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

    java -jar target/distrodiff-1.0-SNAPSHOT.jar ~/opt/jboss-fuse-full-6.1.0.redhat-379.zip  ~/opt/jboss-fuse-full-6.1.0.redhat-387.zip 
