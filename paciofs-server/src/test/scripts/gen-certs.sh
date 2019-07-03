#!/bin/bash

if [ ! -e "$(pwd)/gen-certs.sh" ]; then
  echo "Must be run from this directory"
  exit 1
fi

# see openssl.cnf
mkdir -p ./paciofsCA

# clean
rm *.p12
rm *.pass
rm *.pem
rm -rf ./paciofsCA/*

# processBlocks database
touch ./paciofsCA/index.txt

# serial number file
echo "01" > ./paciofsCA/serial

# create self-signed certificate authority
mkdir ./paciofsCA/private
openssl genrsa -out ./paciofsCA/private/cakey.pem 2048
openssl req -new -x509 -key ./paciofsCA/private/cakey.pem -out ./paciofsCA/cacert.pem -subj "/C=DE/ST=Berlin/L=Berlin/O=ZIB/OU=DAS/CN=ca.paciofs.zib.de" -days 365 -batch

# generate private keys and certificate signing request
openssl req -config ./openssl.cnf -new -out client.csr -newkey rsa:2048 -keyout clientkey.pem -subj "/C=DE/ST=Berlin/L=Berlin/O=ZIB/OU=DAS/CN=client.paciofs.zib.de" -nodes -sha256 -batch
openssl req -config ./openssl.cnf -new -out server.csr -newkey rsa:2048 -keyout serverkey.pem -subj "/C=DE/ST=Berlin/L=Berlin/O=ZIB/OU=DAS/CN=server.paciofs.zib.de" -nodes -sha256 -batch

# sign server and client certificates
mkdir ./paciofsCA/newcerts

# append alternative names before signing, since they are not carried over from the CSRs
echo "DNS.1 = client.paciofs.zib.de" >> ./openssl.cnf
openssl ca -config ./openssl.cnf -in client.csr -out clientcert.pem -md sha256 -batch -days 365
rm client.csr

# remove again
tail -n 1 ./openssl.cnf | wc -c | xargs -I {} truncate ./openssl.cnf -s -{}

# repeat for server, which has to have its host as subject alternative name
echo "DNS.1 = server.paciofs.zib.de" >> ./openssl.cnf
echo "DNS.2 = localhost" >> ./openssl.cnf
echo "DNS.3 = 127.0.0.1" >> ./openssl.cnf
echo "DNS.4 = $(uname -n)" >> ./openssl.cnf
openssl ca -config ./openssl.cnf -in server.csr -out servercert.pem -md sha256 -batch -days 365
rm server.csr
tail -n 4 ./openssl.cnf | wc -c | xargs -I {} truncate ./openssl.cnf -s -{}

# export certificates to p12 for server
pwgen -Bs 10 1 > servercert.p12.pass
pwgen -Bs 10 1 > cacert.p12.pass
openssl pkcs12 -export -in servercert.pem -inkey serverkey.pem -out servercert.p12 -name "server" -passout file:servercert.p12.pass
openssl pkcs12 -export -in ./paciofsCA/cacert.pem -inkey ./paciofsCA/private/cakey.pem -out cacert.p12 -name "ca" -passout file:cacert.p12.pass

# install certificates
cp servercert.p12 ../resources/certs/
cp servercert.p12.pass ../resources/certs/
cp cacert.p12 ../resources/certs/
cp cacert.p12.pass ../resources/certs/

cp ./paciofsCA/cacert.pem ../../../../paciofs-client/test/certs/
cp clientcert.pem ../../../../paciofs-client/test/certs/
cp clientkey.pem ../../../../paciofs-client/test/certs/
