[![](https://github.com/imagej/ImageJ/actions/workflows/build-main.yml/badge.svg)](https://github.com/imagej/ImageJ/actions/workflows/build-main.yml)

# ImageJ

ImageJ is [public domain] software for processing and analyzing scientific
images.

It is written in Java, which allows it to run on many different platforms.

For further information, see:

* The [ImageJ website], the primary home of this project.
* The [ImageJ wiki], a community-built knowledge base covering ImageJ and
  its derivatives and flavors, including [ImageJ2], [Fiji], and others.
* The [ImageJ mailing list] and [Image.sc Forum] for community support.
* The [Contributing] page of the ImageJ wiki for details on how to contribute.

## Using ImageJ as a dependency

To use ImageJ as a library in your [Maven] project, add the dependency:

```xml
<dependency>
  <groupId>net.imagej</groupId>
  <artifactId>ij</artifactId>
  <version>1.53j</version>
</dependency>
```

Where `1.53j` is the version of ImageJ you would like to use:

* Versions up to 1.48q are [in the SciJava Maven repository](https://maven.scijava.org/content/repositories/releases/net/imagej/ij/).
* Versions starting with 1.48r are [on Maven Central](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22net.imagej%22%20AND%20a%3A%22ij%22).
* Be aware that versions prior to 1.53k may not identically match the
  corresponding release tags here in this repository.

## Building from source

### With Ant

The [Apache Ant] utility will compile and run ImageJ using the
`build.xml` file in this directory. There is a version of Ant at

    https://imagej.nih.gov/ij/download/tools/ant/ant.zip

set up to use the JVM distributed with the Windows version of ImageJ.
The README included in the ZIP archive has more information.

### With Maven

You can compile and run ImageJ using the [Maven build tool]:

| Command               | Action                                                                |
|-----------------------|-----------------------------------------------------------------------|
| `mvn`                 | Compile and package ImageJ into a JAR file in the `target` directory. |
| `mvn -Pexec`          | Compile and then run ImageJ.                                          |
| `mvn javadoc:javadoc` | Generate the project Javadoc in the `target/apidocs` directory.       |

[public domain]: https://imagej.nih.gov/ij/disclaimer.html
[ImageJ website]: https://imagej.nih.gov/ij/
[ImageJ wiki]: https://imagej.net/
[ImageJ2]: https://imagej.net/software/imagej2
[Fiji]: https://imagej.net/software/fiji
[ImageJ mailing list]: https://imagej.nih.gov/ij/list.html
[Image.sc Forum]: https://forum.image.sc/tag/imagej
[Contributing]: https://imagej.net/contribute/
[Maven]: https://imagej.net/develop/maven
[Apache Ant]: https://ant.apache.org/
