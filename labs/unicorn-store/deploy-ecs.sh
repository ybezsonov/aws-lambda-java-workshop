#bin/sh

pushd infrastructure/cdk
cdk deploy UnicornStoreSpringECS --outputs-file target/output-ecs.json --require-approval never
popd
