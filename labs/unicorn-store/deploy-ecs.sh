#bin/sh

pushd infrastructure/cdk
cdk deploy UnicornStoreSpringECS --outputs-file target/output.json --require-approval never
popd
