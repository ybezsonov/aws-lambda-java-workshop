#bin/sh

pushd infrastructure/cdk
cdk deploy UnicornStoreSpringEKS --outputs-file target/output-eks.json --require-approval never
popd
