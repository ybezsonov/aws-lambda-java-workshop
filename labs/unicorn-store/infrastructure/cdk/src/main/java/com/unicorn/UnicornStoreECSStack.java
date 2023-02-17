package com.unicorn;

import com.unicorn.constructs.UnicornStoreSpringECS;
import com.unicorn.core.InfrastructureStack;
import software.amazon.awscdk.*;
import software.constructs.Construct;

public class UnicornStoreECSStack extends Stack {

    public UnicornStoreECSStack(final Construct scope, final String id, final StackProps props,
                             final InfrastructureStack infrastructureStack) {
        super(scope, id, props);

        new UnicornStoreSpringECS(this, "UnicornStoreSpringECS", infrastructureStack);
    }
}

