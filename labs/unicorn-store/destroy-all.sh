#bin/sh
date
start=`date +%s`

cd ~/environment/unicorn-store-spring
copilot app delete --yes

cd ~/environment/aws-java-workshop/labs/unicorn-store

for x in `aws ecr list-images --repository-name unicorn-store-spring --query 'imageIds[*][imageDigest]' --output text`; do aws ecr batch-delete-image --repository-name unicorn-store-spring --image-ids imageDigest=$x; done
for x in `aws ecr list-images --repository-name unicorn-store-spring --query 'imageIds[*][imageDigest]' --output text`; do aws ecr batch-delete-image --repository-name unicorn-store-spring --image-ids imageDigest=$x; done

kubectl delete namespace unicorn-store-spring
flux uninstall --silent

pushd infrastructure/cdk
cdk destroy UnicornStoreSpringEKS --force

eksctl delete cluster --name unicorn-store-spring

export GITOPS_USER=unicorn-store-spring-gitops
export GITOPSC_REPO_NAME=unicorn-store-spring-gitops
export CC_POLICY_ARN=$(aws iam list-policies --query 'Policies[?PolicyName==`AWSCodeCommitPowerUser`].{ARN:Arn}' --output text)

aws iam detach-user-policy --user-name $GITOPS_USER --policy-arn $CC_POLICY_ARN
export SSC_ID=$(aws iam list-service-specific-credentials --user-name $GITOPS_USER --query 'ServiceSpecificCredentials[0].ServiceSpecificCredentialId' --output text)
aws iam delete-service-specific-credential --user-name $GITOPS_USER --service-specific-credential-id $SSC_ID
aws iam delete-user --user-name $GITOPS_USER
aws codecommit delete-repository --repository-name $GITOPSC_REPO_NAME

cdk destroy UnicornStoreSpringECS --force
cdk destroy UnicornStoreSpringCI --force

aws apprunner delete-vpc-connector --vpc-connector-arn $(aws apprunner list-vpc-connectors  --query "VpcConnectors[?VpcConnectorName == 'unicornstore-vpc-connector'].VpcConnectorArn" --output text)

export CLOUD9_VPC_ID=$(curl -s http://169.254.169.254/latest/meta-data/network/interfaces/macs/$( ip address show dev eth0 | grep ether | awk ' { print $2  } ' )/vpc-id)

aws ec2 delete-vpc-peering-connection --vpc-peering-connection-id $(aws ec2 describe-vpc-peering-connections --filters "Name=requester-vpc-info.vpc-id,Values=$CLOUD9_VPC_ID" --query 'VpcPeeringConnections[0].VpcPeeringConnectionId' --output text)

cdk destroy UnicornStoreInfrastructure --force
cdk destroy UnicornStoreVpc --force

popd
date
end=`date +%s`
runtime=$((end-start))
echo $runtime
