#bin/sh

app=$1

location=""

if [ $app == "ecs" ]
then
  location=$(cat infrastructure/cdk/target/output-ecs.json | jq -r '.UnicornStoreSpringECS.UnicornStoreServiceURL')
fi

if [ $app == "eks" ]
then
  location=$(cat infrastructure/cdk/target/output-eks.json | jq -r '.UnicornStoreSpringEKS.UnicornStoreServiceURL')
fi

id=$(curl --location --request POST $location'/unicorns' \
  --header 'Content-Type: application/json' \
  --data-raw '{
    "name": "Something",
    "age": "20",
    "type": "Animal",
    "size": "Very big"
}' | jq -r '.id')
echo POST ...
echo id=$id
echo GET id=$id ...
curl --location --request GET $location'/unicorns/'$id | jq
echo PUT ...
curl --location --request PUT $location'/unicorns/'$id \
  --header 'Content-Type: application/json' \
  --data-raw '{
    "name": "Something smaller",
    "age": "10",
    "type": "Animal",
    "size": "Small"
}' | jq -r
echo GET id=$id ...
curl --location --request GET $location'/unicorns/'$id | jq
echo DELETE id=$id ...
curl --location --request DELETE $location'/unicorns/'$id | jq
echo GET id=$id ...
curl --location --request GET $location'/unicorns/'$id | jq
