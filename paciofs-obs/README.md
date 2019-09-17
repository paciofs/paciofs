# openSUSE Build Service
Create distribution specific packages from source code and package specifications.
You package the source code of your project, along with a `dpkg` and/or `rpm` package description file, and the OBS builds packages for every distribution you have selected.

## PacioFS client on OBS
It is located [here](https://build.opensuse.org/project/show/home:robert-schmidtke:paciofs).
The tab `Repositories` currently only specifies Debian 8 and 9 as build targets.
This is where you would add new distributions, always using their standard repositories.
PacioFS has exactly one package: `paciofs`, which contains the client utilities.
There is no package for the server component of PacioFS.
The package is shown in the `Overview` page.

When clicking the package, a number of Debian specific package files appear, along with the source archive.
This is the OBS project for that package, which is basically versioned using `svn`.
The actual source files are located [within this directory](.), and describe dependencies and build steps to create `deb` files.
Check the [Debian Guide](https://www.debian.org/doc/manuals/maint-guide/) for details.
There is also an [RPM Guide](https://docs.fedoraproject.org/en-US/Fedora_Draft_Documentation/0.1/html/RPM_Guide/index.html), but we currently do not build `rpm` packages.

The following files are important to the build step.
There will be little need to change them, unless dependencies are added.
These files are generated/updated from `*.template` files in this directory automatically during the deploy step, as described below.

### debian.control
Lists build and runtime requirements that will be satisfied during building on OBS and installation on a client machine.
See [this section of the guide](https://www.debian.org/doc/manuals/maint-guide/dreq.en.html#control).

### debian.rules
Specifies steps for building the binary.
See [this section of the guide](https://www.debian.org/doc/manuals/maint-guide/dreq.en.html#rules).

### paciofs.dsc
General information on the project.

## Deploying PacioFS to OBS and triggering a build
This directory contains all necessary files.
You will need to install the `osc` command line utility, which is basically a wrapper around `svn` for interacting with the OBS.

Invoking `mvn deploy` from within `paciofs-obs` executes the following steps.
See [pom.xml](./pom.xml) for properties that are passed.

### [create_source_tarball.sh](./create_source_tarball.sh)
- Create a directory that receives all necessary files for building PacioFS.
- Copy all files that Git version control is aware of (tracked, untracked, modified) to that directory.
- Clone third party sources into that directory.
- Create a local maven repository in that directory which will receive all dependencies. This is necessary because OBS servers are offline and cannot access the internet during building. That is why we need to upload all necessary dependencies as well. It is likely possibly to set up an OBS project which has all the dependencies in it and then simply build against that instead of uploading large amounts of binaries.
- Add the dependencies to the repository, ship CMake and Maven as well to have recent versions of them available during build. Again, using CMake and Maven from dedicated projects on OBS should be possible.
- Package everything into an archive.

### [update_debian_files.sh](./update_debian_files.sh)
- Figure out MD5 and size of the created archive and render `paciofs.dsc.template` into `paciofs.dsc`, as well as `debian.changelog.template` into `debian.changelog` with that information using `envsubst`.
- Render `debian.rules.template` into `debian.rules` with updated cmake and maven versions.

### [deploy_paciofs.sh](./deploy_paciofs.sh)
- Copy all necessary files into a local clone of the OBS project. This requires having executed `osc co home:robert-schmidtke:paciofs` first (this script will alert you).
- Use `osc` to add all files and then commit them to OBS.

You can monitor the build status on the OBS website for the PacioFS project.
If everything went well, the packages are available as repositories to be used from distributions, e.g. [Debian 9](https://download.opensuse.org/repositories/home:/robert-schmidtke:/paciofs/Debian_9.0/).
