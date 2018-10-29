cmake_minimum_required (VERSION 2.6)
project (paciofs_fuse)

set (CMAKE_MODULE_PATH "${CMAKE_MODULE_PATH}" "${CMAKE_SOURCE_DIR}/cmake/modules")

find_package (FUSE REQUIRED)
include_directories ("${FUSE_INCLUDE_DIRS}")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D_FILE_OFFSET_BITS=64")

include_directories (
  "${CMAKE_SOURCE_DIR}/include"
)

add_executable (
  paciofs_fuse
  "${CMAKE_SOURCE_DIR}/src/paciofs_fuse.c"
)

target_link_libraries (
  paciofs_fuse
  "${FUSE_LIBRARIES}"
)