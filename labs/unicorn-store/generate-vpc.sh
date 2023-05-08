#bin/sh

pushd infrastructure/cdk

cdk synth UnicornStoreVpc --validation > ../../infrastructure/vpc.yml

popd

yq -i 'del(.Metadata)' ./infrastructure/vpc.yml
yq -i 'del(.Parameters)' ./infrastructure/vpc.yml
yq -i 'del(.Rules)' ./infrastructure/vpc.yml
yq -i 'del(.Conditions)' ./infrastructure/vpc.yml
yq -i 'del(.Resources.CDKMetadata)' ./infrastructure/vpc.yml
yq -i 'del(.Resources.*.Metadata)' ./infrastructure/vpc.yml
