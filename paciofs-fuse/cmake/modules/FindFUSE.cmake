if (APPLE)
  set (CMAKE_FIND_FRAMEWORK NEVER)

  set (CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D_FILE_OFFSET_BITS=64 -D_DARWIN_USE_64_BIT_INODE")

  find_path (
    FUSE_INCLUDE_DIRS fuse.h
    /usr/local/include/osxfuse
  )

  set (fuse_name osxfuse)
else ()
  set (CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D_FILE_OFFSET_BITS=64")

  find_path (
    FUSE_INCLUDE_DIRS fuse.h
    /usr/local/include
  )

  set (fuse_name fuse)
endif ()

find_library (
  FUSE_LIBRARIES ${fuse_name}
  /usr/local/lib
)

include (FindPackageHandleStandardArgs)
find_package_handle_standard_args (FUSE DEFAULT_MSG FUSE_INCLUDE_DIRS FUSE_LIBRARIES)

mark_as_advanced (FUSE_INCLUDE_DIRS FUSE_LIBRARIES)