#!/bin/bash

# Generate SSL keystore for Spring Boot development
# Run this script to create keystore.p12 file

cd "$(dirname "$0")"
cd src/main/resources

# Generate self-signed certificate
keytool -genkeypair \
  -alias springboot \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -storepass password \
  -validity 365 \
  -dname "CN=localhost, OU=Development, O=InsiderGame, L=Bangkok, ST=Bangkok, C=TH"

echo "✅ keystore.p12 created successfully!"
echo "You can now uncomment SSL configuration in application.properties"

