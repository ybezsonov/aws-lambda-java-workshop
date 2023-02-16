package com.unicorn.constructs;

import com.unicorn.core.InfrastructureStack;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.codebuild.PipelineProject;
import software.amazon.awscdk.services.codebuild.BuildSpec;
import software.amazon.awscdk.services.codebuild.ComputeType;
import software.amazon.awscdk.services.codebuild.LinuxBuildImage;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.BuildEnvironmentVariable;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.StageProps;
import software.amazon.awscdk.services.codepipeline.actions.CodeCommitSourceAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeCommitTrigger;
import software.amazon.awscdk.services.codepipeline.actions.EcrSourceAction;
import software.amazon.awscdk.services.codepipeline.actions.EcsDeployAction;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.Protocol;
import software.amazon.awscdk.services.ec2.SecurityGroup;

public class UnicornStoreSpringECS extends Construct {

    public UnicornStoreSpringECS(final Construct scope, final String id, InfrastructureStack infrastructureStack) {
        super(scope, id);
        final String projectName = "unicorn-store-spring";
        
        Cluster cluster = Cluster.Builder.create(this, 
                projectName + "-cluster").vpc(infrastructureStack.getVpc()).build();
        
        ApplicationLoadBalancedFargateService loadBalancedFargateService = 
            ApplicationLoadBalancedFargateService.Builder.create(this, projectName + "-ecs")
            .cluster(cluster)
            .serviceName(projectName + "-service")
            .memoryLimitMiB(1024)
            .desiredCount(1)
            .cpu(512)
            .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                .image(ContainerImage.fromRegistry(
                    infrastructureStack.getAccount()
                    + ".dkr.ecr."
                    + infrastructureStack.getRegion()
                    + ".amazonaws.com/"
                    + projectName
                    + ":latest")
                    )
                .containerName(projectName)
                .environment(Map.of(
                    "SPRING_DATASOURCE_PASSWORD", infrastructureStack.getDatabaseSecretString(),
                    "SPRING_DATASOURCE_URL", infrastructureStack.getDatabaseJDBCConnectionString(),
                    "SPRING_DATASOURCE_HIKARI_maximumPoolSize", "1")
                )
                .build())
            .loadBalancerName(projectName + "-alb")
            .publicLoadBalancer(true)
            .listenerPort(80)
            .build();
            
        new CfnOutput(scope, "LoadBalancerURL", CfnOutputProps.builder()
            .value("http://" + loadBalancedFargateService.getLoadBalancer().getLoadBalancerDnsName())
            .build());
            
        infrastructureStack.getEventBridge().grantPutEventsTo(
            loadBalancedFargateService.getTaskDefinition().getTaskRole());

        loadBalancedFargateService.getTaskDefinition().findContainer(projectName).addPortMappings(
            PortMapping.builder()
                .containerPort(8080)
                .hostPort(80)
                .build());
                
        SecurityGroup sgAlb = SecurityGroup.Builder.create(scope, projectName +  "-alb-sg")
            .vpc(infrastructureStack.getVpc())
            .allowAllOutbound(true)
            .description("ALB ECS App security group")
            .build();
        sgAlb.addIngressRule(Peer.anyIpv4(), Port.tcp(80), "Allow http inbound from anywhere");
        
        loadBalancedFargateService.getLoadBalancer().getConnections().addSecurityGroup(sgAlb);
        
        SecurityGroup sgApp = SecurityGroup.Builder.create(scope, projectName + "-ecs-sg")
            .vpc(infrastructureStack.getVpc())
            .allowAllOutbound(true)
            .description("ECS App security group")
            .build();
        sgApp.addIngressRule(sgAlb, Port.tcp(80), "Allow http inbound from ALB security group");
        
        loadBalancedFargateService.getService().getConnections().addSecurityGroup(sgApp);

        IRepository ecr = Repository.fromRepositoryName(scope, projectName + "-ecr", projectName + "-ecr");
        ecr.grantPull(loadBalancedFargateService.getTaskDefinition().getExecutionRole());
        
        // deployment construct which listens to ECR events, then deploys to the existing service.
        Artifact sourceOuput = Artifact.artifact(projectName + "-ecr-artifact");
        Artifact buildOuput = Artifact.artifact(projectName + "-ecs-artifact");
        
        EcrSourceAction sourceAction = EcrSourceAction.Builder.create()
            .actionName(projectName + "-pipeline-ecr")
            .repository(ecr)
            .imageTag("latest")
            .output(sourceOuput)
            .build();
        
        EcsDeployAction deployAction = EcsDeployAction.Builder.create()
            .actionName("-pipeline-deploy")
            .input(buildOuput)
            .service(loadBalancedFargateService.getService())
            .build();
            
        PipelineProject codeBuild = PipelineProject.Builder.create(scope, projectName + "-codebuild-deploy")
            .projectName(projectName + "-codebuild-deploy")
            .buildSpec(BuildSpec.fromObject(Map.of(
                "version", "0.2",
                "phases", Map.of(
                    "build", Map.of(
                        "commands", List.of(
                            "echo $(jq -n --arg iu \"$IMAGE_URI\" --arg app \"web\" \'[{name:$app,imageUri:$iu}]\') > imagedefinitions.json",
                            "cat imagedefinitions.json"
                        )
                    )
                ),
                "artifacts", Map.of(
                    "files", List.of(
                        "imagedefinitions.json"
                    )
                )
            )))
            .vpc(infrastructureStack.getVpc())
            .environment(BuildEnvironment.builder()
                .privileged(true)
                .computeType(ComputeType.SMALL)
                .buildImage(LinuxBuildImage.AMAZON_LINUX_2_4)
                .build())
            .environmentVariables(Map.of(
                "IMAGE_URI", BuildEnvironmentVariable.builder()
                    .value(infrastructureStack.getAccount()
                        + ".dkr.ecr."
                        + infrastructureStack.getRegion()
                        + ".amazonaws.com/"
                        + projectName
                        + ":latest")
                    .build()
                    )
                )
            .timeout(Duration.minutes(60))
            .build();
        
        ecr.grantPull(codeBuild);
        
        Pipeline.Builder.create(scope, projectName +  "-pipeline-deploy")
            .pipelineName(projectName + "-pipeline-deploy")
            .crossAccountKeys(false)
            .stages(List.of(
                StageProps.builder()
                    .actions(List.of(
                        sourceAction
                        )
                    )
                    .stageName("Source")
                .build(),
                StageProps.builder()
                    .actions(List.of(
                        CodeBuildAction.Builder.create()
                            .actionName("CodeBuild_imagedefinitions")
                            .input(sourceOuput)
                            .project(codeBuild)
                            .runOrder(1)
                            .build()
                        )
                    )
                    .stageName("Build")
                .build(),
                StageProps.builder()
                    .actions(List.of(
                        deployAction
                        )
                    )
                    .stageName("Deploy")
                .build()
                )
            )
            .build();
    }
}
