package com.unicorn.constructs;

import com.unicorn.core.InfrastructureStack;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.codebuild.PipelineProject;
import software.amazon.awscdk.services.codebuild.BuildSpec;
import software.amazon.awscdk.services.codebuild.ComputeType;
import software.amazon.awscdk.services.codebuild.LinuxBuildImage;
import software.amazon.awscdk.services.codebuild.BuildEnvironment;
import software.amazon.awscdk.services.codebuild.BuildEnvironmentVariable;
import software.amazon.awscdk.services.codepipeline.Pipeline;
import software.amazon.awscdk.services.codepipeline.Artifact;
import software.amazon.awscdk.services.codepipeline.StageProps;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.EcrSourceAction;
import software.amazon.awscdk.services.codepipeline.actions.EcsDeployAction;

import software.amazon.awscdk.Duration;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class UnicornStoreSpringECS extends Construct {

    public UnicornStoreSpringECS(final Construct scope, final String id, InfrastructureStack infrastructureStack) {
        super(scope, id);
        final String projectName = "unicorn-store-spring";

        Cluster cluster = Cluster.Builder.create(scope, projectName + "-cluster")
                .clusterName(projectName)
                .vpc(infrastructureStack.getVpc())
                .containerInsights(true)
                .build();

        ApplicationLoadBalancedFargateService loadBalancedFargateService =
            ApplicationLoadBalancedFargateService.Builder.create(scope, projectName + "-ecs")
            .cluster(cluster)
            .serviceName(projectName)
            .memoryLimitMiB(1024)
            .cpu(512)
            .desiredCount(1)
            .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                .containerName(projectName)
                .image(ContainerImage.fromRegistry(
                    infrastructureStack.getAccount()
                    + ".dkr.ecr."
                    + infrastructureStack.getRegion()
                    + ".amazonaws.com/"
                    + projectName
                    + ":latest")
                    )
                .enableLogging(true)
				.logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder()
					.streamPrefix("ecs/" + projectName)
					.build()))
                .environment(Map.of(
                    "SPRING_DATASOURCE_PASSWORD", infrastructureStack.getDatabaseSecretString(),
                    "SPRING_DATASOURCE_URL", infrastructureStack.getDatabaseJDBCConnectionString(),
                    "SPRING_DATASOURCE_HIKARI_maximumPoolSize", "1")
                )
                .build())
            .loadBalancerName(projectName)
            .publicLoadBalancer(true)
            .build();

        new CfnOutput(scope, "LoadBalancerURL", CfnOutputProps.builder()
            .value("http://" + loadBalancedFargateService.getLoadBalancer().getLoadBalancerDnsName())
            .build());

        infrastructureStack.getEventBridge().grantPutEventsTo(
            loadBalancedFargateService.getTaskDefinition().getTaskRole());

        IRepository ecr = Repository.fromRepositoryName(scope, projectName + "-ecr", projectName);
        ecr.grantPull(loadBalancedFargateService.getTaskDefinition().getExecutionRole());

        // deployment construct which listens to ECR events, then deploys to the existing service.
        Artifact sourceOuput = Artifact.artifact(projectName + "-ecr-artifact");
        Artifact buildOuput = Artifact.artifact(projectName + "-ecs-artifact");

        EcrSourceAction sourceAction = EcrSourceAction.Builder.create()
            .actionName(projectName + "-ecr")
            .repository(ecr)
            .imageTag("latest")
            .output(sourceOuput)
            .build();

        EcsDeployAction deployAction = EcsDeployAction.Builder.create()
            .actionName("-deploy")
            .input(buildOuput)
            .service(loadBalancedFargateService.getService())
            .build();

        PipelineProject codeBuild = PipelineProject.Builder.create(scope, projectName + "-codebuild-deploy")
            .projectName(projectName + "-deploy")
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

        Pipeline.Builder.create(scope, projectName +  "-pipeline-deploy")
            .pipelineName(projectName + "-deploy")
            .crossAccountKeys(false)
            .stages(List.of(
                StageProps.builder()
                    .stageName("Source")
                    .actions(List.of(
                        sourceAction
                        )
                    )
                .build(),
                StageProps.builder()
                    .stageName("Build")
                    .actions(List.of(
                        CodeBuildAction.Builder.create()
                            .actionName("CodeBuild_imagedefinitions")
                            .input(sourceOuput)
                            .project(codeBuild)
                            .outputs(List.of(buildOuput))
                            .runOrder(1)
                            .build()
                        )
                    )
                .build(),
                StageProps.builder()
                    .stageName("Deploy")
                    .actions(List.of(
                        deployAction
                        )
                    )
                .build()
                )
            )
            .build();
    }
}
