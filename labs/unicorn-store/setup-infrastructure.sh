#bin/sh

aws apprunner delete-vpc-connector --vpc-connector-arn $(aws apprunner list-vpc-connectors  --query "VpcConnectors[?VpcConnectorName == 'unicornstore-vpc-connector'].VpcConnectorArn" --output text) 2>/dev/null

# Build the database setup function
./mvnw clean package -f infrastructure/db-setup/pom.xml

# Build the unicorn application
./mvnw clean package -f software/alternatives/unicorn-store-basic/pom.xml
./mvnw clean package -f software/unicorn-store-spring/pom.xml
./mvnw clean package -f software/alternatives/unicorn-store-micronaut/pom.xml

# Deploy the infrastructure
pushd infrastructure/cdk

cdk bootstrap

cdk deploy UnicornStoreVpc --require-approval never --outputs-file target/output-vpc.json

cdk deploy UnicornStoreInfrastructure --require-approval never --outputs-file target/output-infra.json

# Execute the DB Setup function to create the table
aws lambda invoke --function-name $(cat target/output-infra.json | jq -r '.UnicornStoreInfrastructure.DbSetupArn') /dev/stdout | cat;

popd

./deploy-vpc-connector.sh

./deploy-vpc-peering.sh
