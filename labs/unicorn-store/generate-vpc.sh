#bin/sh

pushd infrastructure/cdk

cdk synth UnicornStoreVpc --validation > ../../vpc.yml

popd

yq -i 'del(.Metadata)' vpc.yml
yq -i 'del(.Parameters)' vpc.yml
yq -i 'del(.Rules)' vpc.yml
yq -i 'del(.Conditions)' vpc.yml 
yq -i 'del(.Resources.CDKMetadata)' vpc.yml
yq -i 'del(.Resources.*.Metadata)' vpc.yml
