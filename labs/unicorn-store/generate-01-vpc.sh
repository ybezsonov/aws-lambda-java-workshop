#bin/sh

pushd infrastructure/cdk

YML_FILE=vpc

cdk synth UnicornStoreVpc --validation > ../../infrastructure/cfn/$YML_FILE.yml

popd

yq -i 'del(.Metadata)' ./infrastructure/cfn/$YML_FILE.yml
yq -i 'del(.Parameters)' ./infrastructure/cfn/$YML_FILE.yml
yq -i 'del(.Rules)' ./infrastructure/cfn/$YML_FILE.yml
yq -i 'del(.Conditions)' ./infrastructure/cfn/$YML_FILE.yml
yq -i 'del(.Resources.CDKMetadata)' ./infrastructure/cfn/$YML_FILE.yml
yq -i 'del(.Resources.*.Metadata)' ./infrastructure/cfn/$YML_FILE.yml
