#bin/sh

pushd infrastructure/cdk-eks
cdk deploy UnicornStoreSpringEKS --outputs-file ../../cdk/target/output-eks.json --require-approval never
popd
