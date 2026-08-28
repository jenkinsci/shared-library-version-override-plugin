Shared Library Version Override plugin for Jenkins
===================================

[![Build Status](https://ci.jenkins.io/job/Plugins/job/shared-library-version-override-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/shared-library-version-override-plugin/job/master/)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/shared-library-version-override-plugin.svg)](https://github.com/jenkinsci/shared-library-version-override-plugin/graphs/contributors)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/shared-library-version-override.svg)](https://plugins.jenkins.io/shared-library-version-override)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/shared-library-version-override-plugin.svg?label=changelog)](https://github.com/jenkinsci/shared-library-version-override-plugin/releases/latest)
[![MIT License](https://img.shields.io/github/license/jenkinsci/ftp-rename-plugin.svg)](LICENSE)

## Overview

With this plugin, you can configure a specific version of a [Shared Library](https://www.jenkins.io/doc/book/pipeline/shared-libraries/) in folders.

## Setup

- Be sure you have [Pipeline Groovy Libraries](https://plugins.jenkins.io/pipeline-groovy-lib/) installed
- Go to the configuration page of your Folder, Organization Folder, Pipeline Multibranches, ...
- Under The *Shared Library Version Override* section, add a new *Custom Configuration* element
- Optionally set *Filter branch by name (with wildcards)* to restrict the override to matching branch names. The default is `*`, which applies the override to every branch.

![Configuration](doc/assets/configuration.png)

## Security

- A *Global Pipeline Library* without the "Allow default version to be overridden" option, can't be overridden with this plugin, a custom configuration will be skipped.
- An overridden *Global Trusted Pipeline Library* will still be trusted.  
- An overridden *Global Untrusted Pipeline Library* or a *Pipeline Library* defined at Folder level, will still be untrusted so their code runs with sandbox restrictions and cannot use @Grab.  

## LICENSE

Licensed under MIT, see [LICENSE](LICENSE.md)
