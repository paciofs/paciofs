/*
 * Copyright (c) 2018, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "fuse_operations.h"

#include "messages/mode.pb.h"
#include "posix_io.grpc.pb.h"
#include "posix_io_rpc_client.h"

#include <google/protobuf/stubs/common.h>
#include <cerrno>
#include <string>
#include <vector>

// use error values directly without converting to (possibly) platform-specific
// codes
#ifdef VERBATIM_ERRNO
#define TO_ERRNO(error) (error)
#else
#define TO_ERRNO(error) ToErrno(error)
static int ToErrno(paciofs::io::posix::grpc::messages::Errno error);
#endif  // VERBATIM_ERRNO

// same for mode
#ifdef VERBATIM_MODE
#define FROM_MODE(mode) (mode)
#define TO_MODE(mode) (mode)
#else
#define FROM_MODE(mode) FromMode(mode)
#define TO_MODE(mode) ToMode(mode)
static google::protobuf::uint32 FromMode(mode_t mode);
static mode_t ToMode(google::protobuf::uint32 mode);
#endif  // VERBATIM_MODE

static struct fuse_operations_context g_context = {nullptr};

void InitializeFuseOperations(
    paciofs::io::posix::grpc::PosixIoRpcClient *rpc_client,
    fuse_operations &operations) {
  g_context.rpc_client = rpc_client;

  operations.getattr = PfsGetAttr;
  operations.mknod = PfsMkNod;
  operations.mkdir = PfsMkDir;
  operations.chmod = PfsChMod;
  operations.chown = PfsChOwn;
  operations.open = PfsOpen;
  operations.read = PfsRead;
  operations.write = PfsWrite;
  operations.readdir = PfsReadDir;
}

int PfsGetAttr(const char *path, struct stat *buf) {
  namespace messages = paciofs::io::posix::grpc::messages;

  messages::Stat s;
  messages::Errno error = g_context.rpc_client->Stat(std::string(path), s);
  if (error != messages::ERRNO_ESUCCESS) {
    return -TO_ERRNO(error);
  }

  buf->st_dev = s.dev();
  buf->st_ino = s.ino();
  buf->st_mode = TO_MODE(s.mode());
  buf->st_nlink = s.nlink();
  buf->st_uid = s.uid();
  buf->st_gid = s.gid();
  buf->st_rdev = s.rdev();
  buf->st_size = s.size();
#if defined(__linux__)
  buf->st_atim.tv_sec = s.atim().sec();
  buf->st_atim.tv_nsec = s.atim().nsec();
  buf->st_mtim.tv_sec = s.mtim().sec();
  buf->st_mtim.tv_nsec = s.mtim().nsec();
  buf->st_ctim.tv_sec = s.ctim().sec();
  buf->st_ctim.tv_nsec = s.ctim().nsec();
#elif defined(__APPLE__)
  buf->st_atimespec.tv_sec = s.atim().sec();
  buf->st_atimespec.tv_nsec = s.atim().nsec();
  buf->st_mtimespec.tv_sec = s.mtim().sec();
  buf->st_mtimespec.tv_nsec = s.mtim().nsec();
  buf->st_ctimespec.tv_sec = s.ctim().sec();
  buf->st_ctimespec.tv_nsec = s.ctim().nsec();
#else
#error "Unsupported OS"
#endif
  buf->st_blksize = s.blksize();
  buf->st_blocks = s.blocks();

  return 0;
}

int PfsMkNod(const char *path, mode_t mode, dev_t dev) {
  namespace messages = paciofs::io::posix::grpc::messages;

  messages::Errno error =
      g_context.rpc_client->MkNod(path, FromMode(mode), dev);

  if (error != messages::ERRNO_ESUCCESS) {
    return -TO_ERRNO(error);
  }

  return 0;
}

int PfsMkDir(const char *path, mode_t mode) {
  namespace messages = paciofs::io::posix::grpc::messages;

  messages::Errno error = g_context.rpc_client->MkDir(path, FromMode(mode));

  if (error != messages::ERRNO_ESUCCESS) {
    return -TO_ERRNO(error);
  }

  return 0;
}

int PfsChMod(const char *path, mode_t mode) {
  namespace messages = paciofs::io::posix::grpc::messages;

  messages::Errno error = g_context.rpc_client->ChMod(path, mode);

  if (error != messages::ERRNO_ESUCCESS) {
    return -TO_ERRNO(error);
  }

  return 0;
}

int PfsChOwn(const char *path, uid_t uid, gid_t gid) {
  namespace messages = paciofs::io::posix::grpc::messages;

  messages::Errno error = g_context.rpc_client->ChOwn(path, uid, gid);

  if (error != messages::ERRNO_ESUCCESS) {
    return -TO_ERRNO(error);
  }

  return 0;
}

int PfsOpen(const char *path, struct fuse_file_info *fi) {
  namespace messages = paciofs::io::posix::grpc::messages;

  messages::Errno error = g_context.rpc_client->Open(path, fi->flags, fi->fh);

  if (error != messages::ERRNO_ESUCCESS) {
    return -TO_ERRNO(error);
  }

  return 0;
}

int PfsRead(const char *path, char *buf, size_t size, off_t offset,
            struct fuse_file_info *fi) {
  namespace messages = paciofs::io::posix::grpc::messages;

  google::protobuf::uint32 n;
  messages::Errno error =
      g_context.rpc_client->Read(path, buf, size, offset, fi->fh, n);

  if (error != messages::ERRNO_ESUCCESS) {
    return -TO_ERRNO(error);
  }

  return n;
}

int PfsWrite(const char *path, const char *buf, size_t size, off_t offset,
             struct fuse_file_info *fi) {
  namespace messages = paciofs::io::posix::grpc::messages;

  google::protobuf::uint32 n;
  messages::Errno error =
      g_context.rpc_client->Write(path, buf, size, offset, fi->fh, n);

  if (error != messages::ERRNO_ESUCCESS) {
    return -TO_ERRNO(error);
  }

  return n;
}

int PfsReadDir(const char *path, void *buf, fuse_fill_dir_t filler,
               off_t offset, struct fuse_file_info *fi) {
  namespace messages = paciofs::io::posix::grpc::messages;

  std::vector<messages::Dir> dirs;
  messages::Errno error =
      g_context.rpc_client->ReadDir(std::string(path), dirs);
  if (error != messages::ERRNO_ESUCCESS) {
    return -TO_ERRNO(error);
  }

  for (messages::Dir const &dir : dirs) {
    filler(buf, dir.name().c_str(), nullptr, 0);
  }

  return 0;
}

#ifndef VERBATIM_ERRNO
static int ToErrno(paciofs::io::posix::grpc::messages::Errno error) {
  namespace messages = paciofs::io::posix::grpc::messages;

  switch (error) {
    case messages::ERRNO_ESUCCESS:
      return 0;
    case messages::ERRNO_EPERM:
      return EPERM;
    case messages::ERRNO_ENOENT:
      return ENOENT;
    case messages::ERRNO_ESRCH:
      return ESRCH;
    case messages::ERRNO_EINTR:
      return EINTR;
    case messages::ERRNO_EIO:
      return EIO;
    case messages::ERRNO_ENXIO:
      return ENXIO;
    case messages::ERRNO_E2BIG:
      return E2BIG;
    case messages::ERRNO_ENOEXEC:
      return ENOEXEC;
    case messages::ERRNO_EBADF:
      return EBADF;
    case messages::ERRNO_ECHILD:
      return ECHILD;
    case messages::ERRNO_EDEADLK:
      return EDEADLK;
    case messages::ERRNO_ENOMEM:
      return ENOMEM;
    case messages::ERRNO_EACCES:
      return EACCES;
    case messages::ERRNO_EFAULT:
      return EFAULT;
    case messages::ERRNO_EBUSY:
      return EBUSY;
    case messages::ERRNO_EEXIST:
      return EEXIST;
    case messages::ERRNO_EXDEV:
      return EXDEV;
    case messages::ERRNO_ENODEV:
      return ENODEV;
    case messages::ERRNO_ENOTDIR:
      return ENOTDIR;
    case messages::ERRNO_EISDIR:
      return EISDIR;
    case messages::ERRNO_EINVAL:
      return EINVAL;
    case messages::ERRNO_ENFILE:
      return ENFILE;
    case messages::ERRNO_EMFILE:
      return EMFILE;
    case messages::ERRNO_ENOTTY:
      return ENOTTY;
    case messages::ERRNO_ETXTBSY:
      return ETXTBSY;
    case messages::ERRNO_EFBIG:
      return EFBIG;
    case messages::ERRNO_ENOSPC:
      return ENOSPC;
    case messages::ERRNO_ESPIPE:
      return ESPIPE;
    case messages::ERRNO_EROFS:
      return EROFS;
    case messages::ERRNO_EMLINK:
      return EMLINK;
    case messages::ERRNO_EPIPE:
      return EPIPE;
    case messages::ERRNO_EDOM:
      return EDOM;
    case messages::ERRNO_ERANGE:
      return ERANGE;
    case messages::ERRNO_EAGAIN:
      return EAGAIN;
    case messages::ERRNO_EINPROGRESS:
      return EINPROGRESS;
    case messages::ERRNO_EALREADY:
      return EALREADY;
    case messages::ERRNO_ENOTSOCK:
      return ENOTSOCK;
    case messages::ERRNO_EDESTADDRREQ:
      return EDESTADDRREQ;
    case messages::ERRNO_EMSGSIZE:
      return EMSGSIZE;
    case messages::ERRNO_EPROTOTYPE:
      return EPROTOTYPE;
    case messages::ERRNO_ENOPROTOOPT:
      return ENOPROTOOPT;
    case messages::ERRNO_EPROTONOSUPPORT:
      return EPROTONOSUPPORT;
    case messages::ERRNO_ENOTSUP:
      return ENOTSUP;
    case messages::ERRNO_EAFNOSUPPORT:
      return EAFNOSUPPORT;
    case messages::ERRNO_EADDRINUSE:
      return EADDRINUSE;
    case messages::ERRNO_EADDRNOTAVAIL:
      return EADDRNOTAVAIL;
    case messages::ERRNO_ENETDOWN:
      return ENETDOWN;
    case messages::ERRNO_ENETUNREACH:
      return ENETUNREACH;
    case messages::ERRNO_ENETRESET:
      return ENETRESET;
    case messages::ERRNO_ECONNABORTED:
      return ECONNABORTED;
    case messages::ERRNO_ECONNRESET:
      return ECONNRESET;
    case messages::ERRNO_ENOBUFS:
      return ENOBUFS;
    case messages::ERRNO_EISCONN:
      return EISCONN;
    case messages::ERRNO_ENOTCONN:
      return ENOTCONN;
    case messages::ERRNO_ETIMEDOUT:
      return ETIMEDOUT;
    case messages::ERRNO_ECONNREFUSED:
      return ECONNREFUSED;
    case messages::ERRNO_ELOOP:
      return ELOOP;
    case messages::ERRNO_ENAMETOOLONG:
      return ENAMETOOLONG;
    case messages::ERRNO_EHOSTUNREACH:
      return EHOSTUNREACH;
    case messages::ERRNO_ENOTEMPTY:
      return ENOTEMPTY;
    case messages::ERRNO_EDQUOT:
      return EDQUOT;
    case messages::ERRNO_ESTALE:
      return ESTALE;
    case messages::ERRNO_ENOLCK:
      return ENOLCK;
    case messages::ERRNO_ENOSYS:
      return ENOSYS;
    case messages::ERRNO_EOVERFLOW:
      return EOVERFLOW;
    case messages::ERRNO_ECANCELED:
      return ECANCELED;
    case messages::ERRNO_EIDRM:
      return EIDRM;
    case messages::ERRNO_ENOMSG:
      return ENOMSG;
    case messages::ERRNO_EILSEQ:
      return EILSEQ;
    case messages::ERRNO_EBADMSG:
      return EBADMSG;
    case messages::ERRNO_EMULTIHOP:
      return EMULTIHOP;
    case messages::ERRNO_ENODATA:
      return ENODATA;
    case messages::ERRNO_ENOLINK:
      return ENOLINK;
    case messages::ERRNO_ENOSR:
      return ENOSR;
    case messages::ERRNO_ENOSTR:
      return ENOSTR;
    case messages::ERRNO_EPROTO:
      return EPROTO;
    case messages::ERRNO_ETIME:
      return ETIME;
    case messages::ERRNO_ENOTRECOVERABLE:
      return ENOTRECOVERABLE;
    case messages::ERRNO_EOWNERDEAD:
      return EOWNERDEAD;
    default:
      throw std::invalid_argument(messages::Errno_Name(error));
  }
}
#endif  // VERBATIM_ERRNO

#ifndef VERBATIM_MODE
static google::protobuf::uint32 FromMode(mode_t mode) {
  namespace messages = paciofs::io::posix::grpc::messages;

  google::protobuf::uint32 m = 0;
  if ((mode & S_IFBLK) == S_IFBLK) {
    m |= messages::MODE_S_IFBLK;
  }
  if ((mode & S_IFCHR) == S_IFCHR) {
    m |= messages::MODE_S_IFCHR;
  }
  if ((mode & S_IFIFO) == S_IFIFO) {
    m |= messages::MODE_S_IFIFO;
  }
  if ((mode & S_IFREG) == S_IFREG) {
    m |= messages::MODE_S_IFREG;
  }
  if ((mode & S_IFDIR) == S_IFDIR) {
    m |= messages::MODE_S_IFDIR;
  }
  if ((mode & S_IFLNK) == S_IFLNK) {
    m |= messages::MODE_S_IFLNK;
  }
  if ((mode & S_IRUSR) == S_IRUSR) {
    m |= messages::MODE_S_IRUSR;
  }
  if ((mode & S_IWUSR) == S_IWUSR) {
    m |= messages::MODE_S_IWUSR;
  }
  if ((mode & S_IXUSR) == S_IXUSR) {
    m |= messages::MODE_S_IXUSR;
  }
  if ((mode & S_IRGRP) == S_IRGRP) {
    m |= messages::MODE_S_IRGRP;
  }
  if ((mode & S_IWGRP) == S_IWGRP) {
    m |= messages::MODE_S_IWGRP;
  }
  if ((mode & S_IXGRP) == S_IXGRP) {
    m |= messages::MODE_S_IXGRP;
  }
  if ((mode & S_IROTH) == S_IROTH) {
    m |= messages::MODE_S_IROTH;
  }
  if ((mode & S_IWOTH) == S_IWOTH) {
    m |= messages::MODE_S_IWOTH;
  }
  if ((mode & S_IXOTH) == S_IXOTH) {
    m |= messages::MODE_S_IXOTH;
  }
  if ((mode & S_ISUID) == S_ISUID) {
    m |= messages::MODE_S_ISUID;
  }
  if ((mode & S_ISGID) == S_ISGID) {
    m |= messages::MODE_S_ISGID;
  }
  if ((mode & S_ISVTX) == S_ISVTX) {
    m |= messages::MODE_S_ISVTX;
  }

  return m;
}

static mode_t ToMode(google::protobuf::uint32 mode) {
  namespace messages = paciofs::io::posix::grpc::messages;

  unsigned int m = 0;
  if ((mode & messages::MODE_S_IFBLK) == messages::MODE_S_IFBLK) {
    m |= S_IFBLK;
  }
  if ((mode & messages::MODE_S_IFCHR) == messages::MODE_S_IFCHR) {
    m |= S_IFCHR;
  }
  if ((mode & messages::MODE_S_IFIFO) == messages::MODE_S_IFIFO) {
    m |= S_IFIFO;
  }
  if ((mode & messages::MODE_S_IFREG) == messages::MODE_S_IFREG) {
    m |= S_IFREG;
  }
  if ((mode & messages::MODE_S_IFDIR) == messages::MODE_S_IFDIR) {
    m |= S_IFDIR;
  }
  if ((mode & messages::MODE_S_IFLNK) == messages::MODE_S_IFLNK) {
    m |= S_IFLNK;
  }
  if ((mode & messages::MODE_S_IRUSR) == messages::MODE_S_IRUSR) {
    m |= S_IRUSR;
  }
  if ((mode & messages::MODE_S_IWUSR) == messages::MODE_S_IWUSR) {
    m |= S_IWUSR;
  }
  if ((mode & messages::MODE_S_IXUSR) == messages::MODE_S_IXUSR) {
    m |= S_IXUSR;
  }
  if ((mode & messages::MODE_S_IRGRP) == messages::MODE_S_IRGRP) {
    m |= S_IRGRP;
  }
  if ((mode & messages::MODE_S_IWGRP) == messages::MODE_S_IWGRP) {
    m |= S_IWGRP;
  }
  if ((mode & messages::MODE_S_IXGRP) == messages::MODE_S_IXGRP) {
    m |= S_IXGRP;
  }
  if ((mode & messages::MODE_S_IROTH) == messages::MODE_S_IROTH) {
    m |= S_IROTH;
  }
  if ((mode & messages::MODE_S_IWOTH) == messages::MODE_S_IWOTH) {
    m |= S_IWOTH;
  }
  if ((mode & messages::MODE_S_IXOTH) == messages::MODE_S_IXOTH) {
    m |= S_IXOTH;
  }
  if ((mode & messages::MODE_S_ISUID) == messages::MODE_S_ISUID) {
    m |= S_ISUID;
  }
  if ((mode & messages::MODE_S_ISGID) == messages::MODE_S_ISGID) {
    m |= S_ISGID;
  }
  if ((mode & messages::MODE_S_ISVTX) == messages::MODE_S_ISVTX) {
    m |= S_ISVTX;
  }

  return m;
}
#endif  // VERBATIM_MODE
