import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as blueprints from '@aws-quickstart/eks-blueprints';
import * as eks from 'aws-cdk-lib/aws-eks';
import * as ec2 from 'aws-cdk-lib/aws-ec2';

export class CdkEksStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const projectName = 'unicorn-store-spring';
    const repoUrl = 'https://git-codecommit.' + this.region + '.amazonaws.com/v1/repos/' + projectName;

    const bootstrapRepo : blueprints.ApplicationRepository = {
        repoUrl,
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

    const mngProps: blueprints.MngClusterProviderProps = {
        minSize: 1,
        maxSize: 1,
        desiredSize: 1,
        // instanceTypes: [new ec2.InstanceType('m5.large')],
        amiType: eks.NodegroupAmiType.AL2_X86_64,
        nodeGroupCapacityType: eks.CapacityType.ON_DEMAND,
        version: eks.KubernetesVersion.V1_24
    }
    const clusterProvider = new blueprints.MngClusterProvider(mngProps);

    const stackID = 'UnicornStoreSpringEKS'

    // Retrieve VPC information
    const vpc = ec2.Vpc.fromLookup(this, projectName + '-vpc', {
        tags: {
            "unicorn-vpc": "true"
        },
    });

    const platformTeam = new blueprints.PlatformTeam( {
        name: "cluster-admin", // make sure this is unique within organization
        userRoleArn: "arn:aws:iam::" + this.account + ":role/Admin"
    });

    blueprints.EksBlueprint.builder()
        .account(this.account)
        .region(this.region)
        .resourceProvider(blueprints.GlobalResources.Vpc, new blueprints.VpcProvider(vpc.vpcId))
        .clusterProvider(clusterProvider)
        //.version(eks.KubernetesVersion.V1_24)
        .teams(platformTeam)
        .addOns(devSecretStore, devBootstrapArgo)
        .build(scope, stackID);
  }
}
