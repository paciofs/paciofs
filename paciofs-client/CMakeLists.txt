cmake_minimum_required (VERSION 3.14)
project (paciofs_client LANGUAGES CXX VERSION 1.0.0)

set (CMAKE_CXX_STANDARD 14)
set (CMAKE_CXX_STANDARD_REQUIRED ON)
set (CMAKE_CXX_EXTENSIONS OFF)

# we only need filesystem, log, program_options
# the others are static dependencies of these three
set (Boost_USE_STATIC_LIBS "ON")
find_package (Boost ${BOOST_MIN_VERSION} REQUIRED COMPONENTS
  filesystem
  log
  program_options
  system
  date_time
  log_setup
  thread
  regex
  chrono
  atomic
)

find_package (FUSE REQUIRED)

# pkg-config helps us find our libraries in ./third_party
find_package (PkgConfig REQUIRED)
pkg_search_module (Protobuf REQUIRED protobuf=${PROTOBUF_VERSION})
pkg_search_module (gRPC_C REQUIRED grpc=${GRPC_CORE_VERSION})
pkg_search_module (gRPC_CXX REQUIRED grpc++=${GRPC_VERSION})

# needed by gRPC
find_library (CRYPTO_LIBRARY crypto)
find_library (SSL_LIBRARY ssl)
find_library (Z_LIBRARY z)

set (GENERATED_GRPC_DIR "${PROJECT_SOURCE_DIR}/target/generated-sources/grpc")
set (GENERATED_PROTOBUF_DIR "${PROJECT_SOURCE_DIR}/target/generated-sources/protobuf")

# GLOB for sources is not recommended, however we make an exception for generated sources
# https://cmake.org/cmake/help/v3.12/command/file.html#glob-recurse
file (GLOB_RECURSE GRPC_SOURCES CONFIGURE_DEPENDS "${GENERATED_GRPC_DIR}/*.cc")
file (GLOB_RECURSE PROTOBUF_SOURCES CONFIGURE_DEPENDS "${GENERATED_PROTOBUF_DIR}/*.cc")

# object library for gRPC
add_library (grpc_obj OBJECT ${GRPC_SOURCES})
target_include_directories (grpc_obj PRIVATE ${GENERATED_GRPC_DIR} ${GENERATED_PROTOBUF_DIR})
target_include_directories (grpc_obj BEFORE PRIVATE ${gRPC_INCLUDE_DIRS} ${Protobuf_INCLUDE_DIRS})

# object library for Protobuf
add_library (protobuf_obj OBJECT ${PROTOBUF_SOURCES})
target_include_directories (protobuf_obj PRIVATE ${GENERATED_PROTOBUF_DIR})
target_include_directories (protobuf_obj BEFORE PRIVATE ${Protobuf_INCLUDE_DIRS})

add_executable (
  mkfs.paciofs
  "${PROJECT_SOURCE_DIR}/src/logging.cpp"
  "${PROJECT_SOURCE_DIR}/src/mkfs_options.cpp"
  "${PROJECT_SOURCE_DIR}/src/mkfs_paciofs.cpp"
  "${PROJECT_SOURCE_DIR}/src/options.cpp"
  "${PROJECT_SOURCE_DIR}/src/paciofs_rpc_client.cpp"
  "${PROJECT_SOURCE_DIR}/src/rpc_client.cpp"
  $<TARGET_OBJECTS:grpc_obj>
  $<TARGET_OBJECTS:protobuf_obj>
)

target_compile_definitions (mkfs.paciofs PRIVATE PACIOFS_VERSION="${PROJECT_VERSION}")

target_compile_options (mkfs.paciofs PRIVATE -Wall)

target_include_directories (
  mkfs.paciofs
  PRIVATE
  "${PROJECT_SOURCE_DIR}/include"
  "${GENERATED_GRPC_DIR}"
  "${GENERATED_PROTOBUF_DIR}"
  "${Boost_INCLUDE_DIRS}"
)

# prepend include directories to search path to avoid accidentally using the system headers
target_include_directories (
  mkfs.paciofs
  BEFORE PRIVATE
  "${gRPC_INCLUDE_DIRS}"
  "${Protobuf_INCLUDE_DIRS}"
)

target_link_libraries (
  mkfs.paciofs
  "${gRPC_CXX_LINK_LIBRARIES}"
  "${gRPC_C_LINK_LIBRARIES}"
  "${Protobuf_LINK_LIBRARIES}"
  Boost::log
  Boost::program_options
  Boost::system
  Boost::date_time
  Boost::log_setup
  Boost::thread
  Boost::regex
  Boost::chrono
  Boost::atomic
  "${CRYPTO_LIBRARY}"
  "${SSL_LIBRARY}"
  "${Z_LIBRARY}"
)

add_executable (
  mount.paciofs
  "${PROJECT_SOURCE_DIR}/src/fuse_operations.cpp"
  "${PROJECT_SOURCE_DIR}/src/logging.cpp"
  "${PROJECT_SOURCE_DIR}/src/mount_options.cpp"
  "${PROJECT_SOURCE_DIR}/src/mount_paciofs.cpp"
  "${PROJECT_SOURCE_DIR}/src/options.cpp"
  "${PROJECT_SOURCE_DIR}/src/posix_io_rpc_client.cpp"
  "${PROJECT_SOURCE_DIR}/src/rpc_client.cpp"
  $<TARGET_OBJECTS:grpc_obj>
  $<TARGET_OBJECTS:protobuf_obj>
)

target_compile_definitions (mount.paciofs PRIVATE PACIOFS_VERSION="${PROJECT_VERSION}")

# if enabled, error values and file modes are not converted to this platform
# target_compile_definitions (mount.paciofs PRIVATE VERBATIM_ERRNO)
# target_compile_definitions (mount.paciofs PRIVATE VERBATIM_MODE)

target_compile_definitions (mount.paciofs PRIVATE FUSE_USE_VERSION=${FUSE_VERSION})
target_compile_options (mount.paciofs PRIVATE -Wall)

target_include_directories (
  mount.paciofs
  PRIVATE
  "${PROJECT_SOURCE_DIR}/include"
  "${GENERATED_GRPC_DIR}"
  "${GENERATED_PROTOBUF_DIR}"
  "${Boost_INCLUDE_DIRS}"
)

# prepend include directories to search path to avoid accidentally using the system headers
target_include_directories (
  mount.paciofs
  BEFORE PRIVATE
  "${FUSE_INCLUDE_DIRS}"
  "${gRPC_INCLUDE_DIRS}"
  "${Protobuf_INCLUDE_DIRS}"
)

target_link_libraries (
  mount.paciofs
  "${FUSE_LIBRARIES}"
  "${gRPC_CXX_LINK_LIBRARIES}"
  "${gRPC_C_LINK_LIBRARIES}"
  "${Protobuf_LINK_LIBRARIES}"
  Boost::filesystem
  Boost::log
  Boost::program_options
  Boost::system
  Boost::date_time
  Boost::log_setup
  Boost::thread
  Boost::regex
  Boost::chrono
  Boost::atomic
  "${CRYPTO_LIBRARY}"
  "${SSL_LIBRARY}"
  "${Z_LIBRARY}"
)

install (
  TARGETS mkfs.paciofs mount.paciofs
  PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE GROUP_READ GROUP_EXECUTE WORLD_READ WORLD_EXECUTE
)

if (APPLE)
  set (CPACK_GENERATOR "productbuild")
else ()
  set (CPACK_GENERATOR "DEB" "RPM")

  # general package information
  set (CPACK_PACKAGE_CONTACT "Zuse Institute Berlin")
  set (CPACK_PACKAGE_DESCRIPTION_SUMMARY "Distributed, fault-tolerant and tamper-proof file system")
  set (CPACK_PACKAGE_VENDOR "Zuse Institute Berlin")

  # DEB-specific
  set (CPACK_DEBIAN_PACKAGE_DEPENDS "\
    fuse (>= 2.9.3), \
    libssl1.1 \
  ")

  # RPM-specific
  set (CPACK_RPM_PACKAGE_REQUIRES "\
    fuse >= 2.9.3, \
    libopenssl1_1 \
  ")

endif()
include (CPack)
