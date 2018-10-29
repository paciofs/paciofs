find_path (
  FUSE_INCLUDE_DIRS fuse.h
  /usr/local/include/osxfuse
)

if (APPLE)
  set (fuse_name osxfuse)
else ()
  set (fuse_name fuse)
endif ()

find_library (
  FUSE_LIBRARIES ${fuse_name}
  /usr/local/lib
)

include (FindPackageHandleStandardArgs)
find_package_handle_standard_args (FUSE DEFAULT_MSG FUSE_INCLUDE_DIRS FUSE_LIBRARIES)

mark_as_advanced (FUSE_INCLUDE_DIRS FUSE_LIBRARIES)