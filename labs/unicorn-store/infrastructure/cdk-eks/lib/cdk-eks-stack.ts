import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as blueprints from '@aws-quickstart/eks-blueprints';
import * as eks from 'aws-cdk-lib/aws-eks';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as secret from "aws-cdk-lib/aws-secretsmanager";
import * as events from "aws-cdk-lib/aws-events";
import * as iam from "aws-cdk-lib/aws-iam";
import * as ecr from "aws-cdk-lib/aws-ecr";

const clusterName = "eks-blueprints-workloads";

export class CdkEksStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const bootstrapRepo : blueprints.ApplicationRepository = {
        repoUrl: 'https://git-codecommit.' + this.region + '.amazonaws.com/v1/repos/' + clusterName + '-cc',
        targetRevision: 'main',
    }

    // HERE WE GENERATE THE ADDON CONFIGURATIONS
    const devSecretStore = new blueprints.SecretsStoreAddOn({
    });
    const devBootstrapArgo = new blueprints.ArgoCDAddOn({
        bootstrapRepo: {
            ...bootstrapRepo,
            path: 'envs/dev',
        },
        values: { server: {service: { type: "LoadBalancer"} } }
    });

    const clusterProvider = new blueprints.GenericClusterProvider({
        clusterName: clusterName,
        version: eks.KubernetesVersion.V1_24,
        managedNodeGroups: [
            {
                id: "mng-ondemand",
                minSize: 1,
                maxSize: 1,
                desiredSize: 1,
                nodeGroupCapacityType: eks.CapacityType.ON_DEMAND,
                nodeGroupSubnets: {
                    subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
                },
                amiType: eks.NodegroupAmiType.AL2_X86_64,
                //instanceTypes: [new ec2.InstanceType('m5.large')]
            },
            // {
            //     id: "mng2-spot",
            //     instanceTypes: [ec2.InstanceType.of(ec2.InstanceClass.BURSTABLE3, ec2.InstanceSize.MEDIUM)],
            //     nodeGroupCapacityType: eks.CapacityType.SPOT
            // }
        ],
        fargateProfiles: {
            "workloads": {
                fargateProfileName: "workloads",
                selectors:  [{ namespace: "worloads" }],
                subnetSelection: {
                    subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
                },
            },
            "unicorn-store-spring": {
                fargateProfileName: "unicorn-store-spring",
                selectors:  [{ namespace: "unicorn-store-spring" }],
                subnetSelection: {
                    subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
                },
                // podExecutionRole: rolePodUnicornStoreSpring,
            }
        }
    });

    const stackID = 'UnicornStoreSpringEKS'

    // Retrieve VPC information
    const vpc = ec2.Vpc.fromLookup(this, 'unicorn-vpc', {
        tags: {
            "unicorn": "true"
        },
    });

    const platformTeam = new blueprints.PlatformTeam( {
        name: "platform-team", // make sure this is unique within organization
        userRoleArn: "arn:aws:iam::" + this.account + ":role/Admin"
    });

    const blueprint = blueprints.EksBlueprint.builder()
        .account(this.account)
        .region(this.region)
        .resourceProvider(blueprints.GlobalResources.Vpc, new blueprints.VpcProvider(vpc.vpcId))
        .clusterProvider(clusterProvider)
        .teams(platformTeam)
        .addOns(devSecretStore, devBootstrapArgo)
        .build(scope, stackID);
    }
}
