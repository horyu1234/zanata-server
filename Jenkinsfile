#!/usr/bin/env groovy

// these wrappers don't seem to be built in yet
def void ansicolor(Closure<Void> wrapped) {
  wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm', 'defaultFg': 1, 'defaultBg': 2]) {
    wrapped()
  }
}
def void xvfb(Closure<Void> wrapped) {
  wrap([$class: 'Xvfb']) {
    wrapped()
  }
}


timestamps {
  node('Fedora') {
    ansicolor {
      // Mark the code checkout 'stage'....
      stage 'Checkout'

      // Checkout code from repository
      checkout scm

      stage 'Install build tools'
      // TODO yum install the following
      // Xvfb libaio xorg-x11-server-Xvfb wget unzip git-core
      // https://dl.google.com/linux/direct/google-chrome-stable_current_x86_64.rpm
      // gcc-c++
      // java-devel; set alternatives for java
      // groovy firefox rpm-build docker
      // download chromedriver

      // Note: if next step happens on another node, mvnw might have to download again
      sh "./mvnw --version"

      // Mark the code build 'stage'....
      stage 'Build and test'

      // TODO build and archive binaries without tests, then in parallel, unarchive and run:
      //   unit tests
      //   arquillian tests
      //   eap, wildfly functional tests
      //   mysql 5.6, functional tests (on wildfly)
      //   and (later) mariadb 10 functional tests (on wildfly)

      xvfb {
        def ports = sh(script: 'etc/scripts/allocate-jboss-ports', returnStdout: true)
        withEnv(ports.trim().readLines()) {
          // Run the maven build
          sh """./mvnw clean verify \
                       --batch-mode \
                       --settings .travis-settings.xml \
                       --update-snapshots \
                       -DstaticAnalysis \
                       -Dchromefirefox \
                       -Dappserver=wildfly8 \
                       -Dmaven.test.redirectTestOutputToFile \
          """
        }
      }
    }
  }
}
