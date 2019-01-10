cmake_minimum_required (VERSION 2.6)
project (paciofs_fuse)

set (CMAKE_MODULE_PATH "${CMAKE_MODULE_PATH}" "${CMAKE_SOURCE_DIR}/cmake/modules")

find_package (FUSE REQUIRED)

include_directories (
  "${CMAKE_SOURCE_DIR}/include"
  "${CMAKE_SOURCE_DIR}/build/grpc"
  "${FUSE_INCLUDE_DIRS}"
  "${PROTOBUF_INCLUDE_DIRS}"
)

add_executable (
  paciofs_fuse
  "${CMAKE_SOURCE_DIR}/src/paciofs_fuse.cpp"
)

target_link_libraries (
  paciofs_fuse
  "${FUSE_LIBRARIES}"
)