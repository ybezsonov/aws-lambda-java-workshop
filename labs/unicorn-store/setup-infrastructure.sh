#bin/sh

# Build the database setup function
./mvnw clean package -f infrastructure/db-setup/pom.xml

# Build the unicorn application
./mvnw clean package -f software/alternatives/unicorn-store-basic/pom.xml
./mvnw clean package -f software/unicorn-store-spring/pom.xml
./mvnw clean package -f software/alternatives/unicorn-store-micronaut/pom.xml

# Deploy the infrastructure
cd infrastructure/cdk

cdk bootstrap
cdk deploy UnicornStoreInfrastructure --require-approval never --outputs-file target/output.json

# Execute the DB Setup function to create the table
aws lambda invoke --function-name $(cat target/output.json | jq -r '.UnicornStoreInfrastructure.DbSetupArn') /dev/stdout | cat;

# add VPC connector

export UNICORN_VPC_ID=$(aws cloudformation describe-stacks --stack-name UnicornStoreVpc \
--query 'Stacks[0].Outputs[?OutputKey==`idUnicornStoreVPC`].OutputValue' --output text)

export UNICORN_SUBNET_PRIVATE_1=$(aws ec2 describe-subnets \
--filters "Name=vpc-id,Values=$UNICORN_VPC_ID" "Name=tag:Name,Values=UnicornStoreVpc/UnicornVpc/PrivateSubnet1" \
--query 'Subnets[0].SubnetId' --output text)

export UNICORN_SUBNET_PRIVATE_2=$(aws ec2 describe-subnets \
--filters "Name=vpc-id,Values=$UNICORN_VPC_ID" "Name=tag:Name,Values=UnicornStoreVpc/UnicornVpc/PrivateSubnet2" \
--query 'Subnets[0].SubnetId' --output text)

aws apprunner create-vpc-connector --vpc-connector-name unicornstore-vpc-connector \
--subnets $UNICORN_SUBNET_PRIVATE_1 $UNICORN_SUBNET_PRIVATE_2
