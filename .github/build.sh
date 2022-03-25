#!/bin/sh
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/main/ci-build.sh &&
sh ci-build.sh && {
  die() { echo "[ERROR] $1"; exit 1; }

  echo
  echo "== Extracting ImageJ version from source code =="
  version=$(grep -o ' VERSION *= *"[^"]*' ij/ImageJ.java) ||
    die "Failed to extract version from ij/ImageJ.java"
  build=$(grep -o ' BUILD *= *"[^"]*' ij/ImageJ.java) ||
    die "Failed to extract build number from ij/ImageJ.java"
  version=${version#*\"}
  build=${build#*\"}
  echo "VERSION = $version"
  echo "BUILD = $build"

  if [ "$build" = "" ]
  then
    # Sanity checks.
    test "$version" || die "Empty version string!"
    echo "$version" | grep -q '^[0-9]\+\.[0-9]\+[a-z]$' ||
      die "Unexpected format for version string $version"

    echo
    echo "== Releasing ImageJ v$version =="

    # Tweak the Maven POM to reflect the release version.
    cat pom.xml | sed -e "s/1\.x-SNAPSHOT/$version/" -e "s/<tag>HEAD</<tag>v$version</" > pom.new &&
      mv -f pom.new pom.xml ||
      die "Failed to adjust pom.xml to match release version $version"

    # Deploy the release.
    mvn -Psonatype-oss-release -B -Djdk.tls.client.protocols="TLSv1,TLSv1.1,TLSv1.2" deploy
  else
    echo
    echo "== Skipping release for daily build $version$build =="
  fi
}
