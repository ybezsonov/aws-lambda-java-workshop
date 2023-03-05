#bin/sh

pushd infrastructure/cdk
cdk destroy UnicornStoreSpringEKS --force
cdk destroy UnicornStoreSpringECS --force
cdk destroy UnicornStoreSpringCI --force
cdk destroy UnicornStoreInfrastructure --force
popd
