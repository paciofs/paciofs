#!/bin/bash

# see openssl.cnf
mkdir -p ./paciofsCA

# clean
rm *.pem
rm -rf ./paciofsCA/*

# index database
touch ./paciofsCA/index.txt

# serial number file
echo "01" > ./paciofsCA/serial

# generate private keys and certificate signing requests
mkdir ./paciofsCA/private
openssl req -config ./openssl.cnf -new -out ca.csr -newkey rsa:2048 -keyout ./paciofsCA/private/cakey.pem -subj "/C=DE/ST=Berlin/L=Berlin/O=ZIB/OU=DAS/CN=ca.paciofs.zib.de" -nodes -sha256 -batch
openssl req -config ./openssl.cnf -new -out server.csr -newkey rsa:2048 -keyout serverkey.pem -subj "/C=DE/ST=Berlin/L=Berlin/O=ZIB/OU=DAS/CN=server.paciofs.zib.de" -nodes -sha256 -batch
openssl req -config ./openssl.cnf -new -out client.csr -newkey rsa:2048 -keyout clientkey.pem -subj "/C=DE/ST=Berlin/L=Berlin/O=ZIB/OU=DAS/CN=client.paciofs.zib.de" -nodes -sha256 -batch

# create self-signed root certificate
openssl x509 -req -in ca.csr -signkey ./paciofsCA/private/cakey.pem -out ./paciofsCA/cacert.pem -sha256
rm ca.csr

# sign server and client certificates
mkdir ./paciofsCA/newcerts
openssl ca -config ./openssl.cnf -in server.csr -out server.pem -md sha256 -batch
rm server.csr
openssl ca -config ./openssl.cnf -in client.csr -out client.pem -md sha256 -batch
rm client.csr

# create the chain for the client
cat server.pem > chain.pem
cat ./paciofsCA/cacert.pem >> chain.pem
