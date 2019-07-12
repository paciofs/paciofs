/*
 * Copyright (c) 2019, Zuse Institute Berlin.
 *
 * Licensed under the New BSD License, see LICENSE file for details.
 *
 */

#include "posix_io_rpc_client.h"

#include "logging.h"
#include "posix_io.grpc.pb.h"
#include "rpc_client.h"

#include <grpcpp/grpcpp.h>
#include <sys/stat.h>
#include <ctime>
#include <string>
#include <vector>

namespace paciofs {
namespace io {
namespace posix {
namespace grpc {

PosixIoRpcClient::PosixIoRpcClient(std::string const &target,
                                   std::string const &volume_name,
                                   bool async_writes,
                                   std::string const &cert_chain,
                                   std::string const &private_key,
                                   std::string const &root_certs)
    : RpcClient<PosixIoService>(target, cert_chain, private_key, root_certs),
      async_writes_(async_writes),
      async_write_completion_queue_(
          std::make_unique<::grpc::CompletionQueue>()),
      volume_name_(volume_name),
      logger_(paciofs::logging::Logger()) {
  // needed for the creation of tags that identify asynchronous write requests
  srand(time(nullptr));
}

PosixIoRpcClient::~PosixIoRpcClient() {
  async_write_completion_queue_->Shutdown();
}

bool PosixIoRpcClient::Ping() {
  PingRequest request;
  logger_.Trace([request](auto &out) {
    out << "Ping(" << request.ShortDebugString() << ")";
  });

  PingResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
  ::grpc::Status status = Stub()->Ping(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "Ping(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "Ping(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });
  }

  return status.ok();
}

messages::Errno PosixIoRpcClient::Stat(std::string const &path,
                                       messages::Stat &stat) {
  StatRequest request;
  request.set_path(PreparePath(path));
  logger_.Trace([request](auto &out) {
    out << "Stat(" << request.ShortDebugString() << ")";
  });

  StatResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
  ::grpc::Status status = Stub()->Stat(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "Stat(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });

    messages::Errno error = response.error();
    if (error != messages::ERRNO_ESUCCESS) {
      return error;
    }

    stat.CopyFrom(response.stat());
    return messages::ERRNO_ESUCCESS;
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "Stat(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });

    return messages::ERRNO_EIO;
  }
}

messages::Errno PosixIoRpcClient::MkNod(std::string const &path,
                                        google::protobuf::uint32 mode,
                                        google::protobuf::int32 dev) {
  MkNodRequest request;
  request.set_path(PreparePath(path));
  request.set_mode(mode);
  request.set_dev(dev);
  logger_.Trace([request](auto &out) {
    out << "MkNod(" << request.ShortDebugString() << ")";
  });

  MkNodResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
  ::grpc::Status status = Stub()->MkNod(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "MkNod(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });

    return response.error();
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "MkNod(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });

    return messages::ERRNO_EIO;
  }
}

messages::Errno PosixIoRpcClient::MkDir(std::string const &path,
                                        google::protobuf::uint32 mode) {
  MkDirRequest request;
  request.set_path(PreparePath(path));
  request.set_mode(mode);
  logger_.Trace([request](auto &out) {
    out << "MkDir(" << request.ShortDebugString() << ")";
  });

  MkDirResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
  ::grpc::Status status = Stub()->MkDir(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "MkDir(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });

    return response.error();
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "MkDir(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });

    return messages::ERRNO_EIO;
  }
}

messages::Errno PosixIoRpcClient::ChMod(std::string const &path,
                                        google::protobuf::uint32 mode) {
  ChModRequest request;
  request.set_path(PreparePath(path));
  request.set_mode(mode);
  logger_.Trace([request](auto &out) {
    out << "ChMod(" << request.ShortDebugString() << ")";
  });

  ChModResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
  ::grpc::Status status = Stub()->ChMod(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "ChMod(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });

    return response.error();
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "ChMod(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });

    return messages::ERRNO_EIO;
  }
}

messages::Errno PosixIoRpcClient::ChOwn(std::string const &path,
                                        google::protobuf::uint32 uid,
                                        google::protobuf::uint32 gid) {
  ChOwnRequest request;
  request.set_path(PreparePath(path));
  request.set_uid(uid);
  request.set_gid(gid);
  logger_.Trace([request](auto &out) {
    out << "ChOwn(" << request.ShortDebugString() << ")";
  });

  ChOwnResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
  ::grpc::Status status = Stub()->ChOwn(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "ChOwn(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });

    return response.error();
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "ChOwn(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });

    return messages::ERRNO_EIO;
  }
}

messages::Errno PosixIoRpcClient::Open(std::string const &path,
                                       google::protobuf::int32 flags,
                                       google::protobuf::uint64 &fh) {
  OpenRequest request;
  request.set_path(PreparePath(path));
  request.set_flags(flags);
  logger_.Trace([request](auto &out) {
    out << "Open(" << request.ShortDebugString() << ")";
  });

  OpenResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
  ::grpc::Status status = Stub()->Open(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "Open(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });

    messages::Errno error = response.error();
    if (error != messages::ERRNO_ESUCCESS) {
      return error;
    }

    fh = response.fh();

    return messages::ERRNO_ESUCCESS;
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "Open(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });

    return messages::ERRNO_EIO;
  }
}

messages::Errno PosixIoRpcClient::Read(std::string const &path, char *buf,
                                       google::protobuf::uint32 size,
                                       google::protobuf::int64 offset,
                                       google::protobuf::uint64 fh,
                                       google::protobuf::uint32 &n) {
  ReadRequest request;
  request.set_path(PreparePath(path));
  request.set_size(size);
  request.set_offset(offset);
  request.set_fh(fh);
  logger_.Trace([request](auto &out) {
    out << "Read(" << request.ShortDebugString() << ")";
  });

  ReadResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
  ::grpc::Status status = Stub()->Read(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      ReadResponse printResponse(response);
      printResponse.clear_buf();
      out << "Read(" << request.ShortDebugString()
          << "): " << printResponse.ShortDebugString();
    });

    if (response.eof()) {
      n = 0;
    } else {
      n = response.n();
      memcpy(buf, response.buf().c_str(), n);
    }

    return messages::ERRNO_ESUCCESS;
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "Read(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });

    return messages::ERRNO_EIO;
  }
}

messages::Errno PosixIoRpcClient::Write(std::string const &path,
                                        const char *buf,
                                        google::protobuf::uint32 size,
                                        google::protobuf::int64 offset,
                                        google::protobuf::uint64 fh,
                                        google::protobuf::uint32 &n) {
  // use a random tag to identify the request once it is done
  void *request_tag = (void *)rand();

  WriteRequest request;
  request.set_path(PreparePath(path));
  request.mutable_buf()->assign(buf, size);
  request.set_size(size);
  request.set_offset(offset);
  request.set_fh(fh);
  logger_.Trace([request, request_tag](auto &out) {
    WriteRequest printRequest(request);
    printRequest.clear_buf();
    out << "Write(" << printRequest.ShortDebugString() << ") (" << request_tag
        << ")";
  });

  ::grpc::ClientContext context;
  SetMetadata(context);

  // make all calls asynchronous
  std::unique_ptr<::grpc::ClientAsyncResponseReader<WriteResponse>> async_write(
      Stub()->PrepareAsyncWrite(&context, request,
                                async_write_completion_queue_.get()));
  async_write->StartCall();

  // notify request_tag once the call is done
  WriteResponse response;
  ::grpc::Status status;
  async_write->Finish(&response, &status, request_tag);

  if (async_writes_) {
    // TODO implement
    return messages::ERRNO_EIO;
  } else {
    // wait for the call
    void *tag;
    bool ok = false;
    bool got_event = async_write_completion_queue_->Next(&tag, &ok);
    if (got_event && ok) {
      // TODO check tag vs. request_tag
      if (status.ok()) {
        logger_.Trace([request, tag, response](auto &out) {
          WriteRequest printRequest(request);
          printRequest.clear_buf();
          out << "Write(" << printRequest.ShortDebugString() << ") (" << tag
              << "): " << response.ShortDebugString();
        });

        n = response.n();

        return messages::ERRNO_ESUCCESS;
      } else {
        logger_.Warning([request, tag, status](auto &out) {
          WriteRequest printRequest(request);
          printRequest.clear_buf();
          out << "Write(" << printRequest.ShortDebugString() << ") (" << tag
              << "): " << status.error_message() << " (" << status.error_code()
              << ")";
        });

        return messages::ERRNO_EIO;
      }
    } else {
      logger_.Warning([request_tag, got_event, ok](auto &out) {
        out << "Could not get event from queue for request " << request_tag
            << " (got " << (got_event ? "" : "no") << " event from queue / "
            << (ok ? "" : "not") << " ok)";
      });

      return messages::ERRNO_EIO;
    }
  }
}

messages::Errno PosixIoRpcClient::ReadDir(std::string const &path,
                                          std::vector<messages::Dir> &dirs) {
  ReadDirRequest request;
  request.set_path(PreparePath(path));
  logger_.Trace([request](auto &out) {
    out << "ReadDir(" << request.ShortDebugString() << ")";
  });

  ReadDirResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
  ::grpc::Status status = Stub()->ReadDir(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "ReadDir(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });

    messages::Errno error = response.error();
    if (error != messages::ERRNO_ESUCCESS) {
      return error;
    }

    for (int i = 0; i < response.dirs_size(); ++i) {
      dirs.push_back(response.dirs(i));
    }

    return messages::ERRNO_ESUCCESS;
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "ReadDir(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });

    return messages::ERRNO_EIO;
  }
}

messages::Errno PosixIoRpcClient::Create(std::string const &path,
                                         google::protobuf::uint32 mode,
                                         google::protobuf::int32 flags,
                                         google::protobuf::uint64 &fh) {
  CreateRequest request;
  request.set_path(PreparePath(path));
  request.set_mode(mode);
  request.set_flags(flags);
  logger_.Trace([request](auto &out) {
    out << "Create(" << request.ShortDebugString() << ")";
  });

  CreateResponse response;
  ::grpc::ClientContext context;
  SetMetadata(context);
  ::grpc::Status status = Stub()->Create(&context, request, &response);

  if (status.ok()) {
    logger_.Trace([request, response](auto &out) {
      out << "Create(" << request.ShortDebugString()
          << "): " << response.ShortDebugString();
    });

    messages::Errno error = response.error();
    if (error != messages::ERRNO_ESUCCESS) {
      return error;
    }

    fh = response.fh();

    return messages::ERRNO_ESUCCESS;
  } else {
    logger_.Warning([request, status](auto &out) {
      out << "Create(" << request.ShortDebugString()
          << "): " << status.error_message() << " (" << status.error_code()
          << ")";
    });

    return messages::ERRNO_EIO;
  }
}

std::string const PosixIoRpcClient::PreparePath(std::string const &path) const {
  std::string prepared_path = path;
  prepared_path = MakeAbsolute(prepared_path);
  prepared_path = PrefixVolumeName(prepared_path);
  return prepared_path;
}

std::string const PosixIoRpcClient::MakeAbsolute(std::string const &path) {
  return path;
}

std::string const PosixIoRpcClient::PrefixVolumeName(
    std::string const &path) const {
  return volume_name_ + ":" + path;
}

}  // namespace grpc
}  // namespace posix
}  // namespace io
}  // namespace paciofs
