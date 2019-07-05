FROM ubuntu:18.04
LABEL maintainer="schmidtke@zib.de"

ENV DEBIAN_FRONTEND="noninteractive"

RUN apt-get --quiet update \
  && apt-get --quiet --yes install --no-install-recommends \
  \
  # system dependencies
  curl \
  unzip \
  wget \
  \
  # build dependencies
  autoconf \
  automake \
  build-essential \
  git \
  libboost-filesystem-dev \
  libboost-log-dev \
  libboost-program-options-dev \
  libboost-system-dev \
  libboost-date-time-dev \
  libboost-thread-dev \
  libboost-regex-dev \
  libboost-chrono-dev \
  libboost-atomic-dev \
  libfuse-dev \
  libssl-dev \
  libtool \
  maven \
  openjdk-11-jdk \
  pkg-config \
  zlib1g-dev \
  \
  # cleanup
  && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp

# install cmake
ENV CMAKE_VERSION="3.14.5"
RUN wget --output-document cmake.tar.gz --quiet https://github.com/Kitware/CMake/releases/download/v${CMAKE_VERSION}/cmake-${CMAKE_VERSION}-Linux-x86_64.tar.gz \
  && tar --directory /usr/local --extract --file cmake.tar.gz --strip-components 1 \
  && rm cmake.tar.gz

# install go
ENV GO_VERSION="1.12.6"
RUN wget --output-document go.tar.gz --quiet https://dl.google.com/go/go${GO_VERSION}.linux-amd64.tar.gz \
  && tar --directory /usr/local --extract --file go.tar.gz \
  && rm go.tar.gz
ENV GOPATH="/go"
ENV GOBIN="${GOPATH}/bin"
ENV PATH="/usr/local/go/bin:${GOBIN}:${PATH}"

# install dep
RUN mkdir -p "${GOBIN}"
ENV DEP_RELEASE_TAG="v0.5.0"
RUN curl --silent https://raw.githubusercontent.com/golang/dep/master/install.sh | sh

# install MultiChain
ENV MULTICHAIN_VERSION="2.0.2"
RUN wget --output-document multichain.tar.gz --quiet https://www.multichain.com/download/multichain-${MULTICHAIN_VERSION}.tar.gz \
  && tar --directory /usr/local --extract --file multichain.tar.gz \
  && rm multichain.tar.gz
ENV MULTICHAIN_HOME="/usr/local/multichain-${MULTICHAIN_VERSION}"

# install PacioFS dependencies
WORKDIR /paciofs
COPY ./pom.xml ./pom.xml
COPY ./paciofs-csi/pom.xml ./paciofs-csi/pom.xml
COPY ./paciofs-client/pom.xml ./paciofs-client/pom.xml
COPY ./paciofs-kubernetes/pom.xml ./paciofs-kubernetes/pom.xml
COPY ./paciofs-server/pom.xml ./paciofs-server/pom.xml
RUN mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.1:go-offline --define silent=true

# build PacioFS
COPY ./paciofs-csi ./paciofs-csi/
COPY ./paciofs-client ./paciofs-client/
COPY ./paciofs-kubernetes ./paciofs-kubernetes/
COPY ./paciofs-server ./paciofs-server/

# install parent pom only first
RUN mvn --non-recursive install

RUN mvn --file ./paciofs-client/third_party/pom.xml install
RUN mvn --activate-profiles docker clean install

# run PacioFS
ENTRYPOINT [ "java", "-jar", "/paciofs/paciofs-server/target/paciofs-server-1.0.0-SNAPSHOT-allinone.jar" ]
CMD [ "--config", "/paciofs/paciofs-server/src/test/resources/local.conf" ]
