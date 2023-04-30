#bin/sh

# Deploy the VPC
cd infrastructure/cdk

cdk bootstrap
cdk deploy UnicornStoreVpc --require-approval never --outputs-file target/output.json
