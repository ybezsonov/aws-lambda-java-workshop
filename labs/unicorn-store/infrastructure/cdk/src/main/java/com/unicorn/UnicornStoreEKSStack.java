package com.unicorn;

import com.unicorn.constructs.UnicornStoreSpringEKS;
import com.unicorn.core.InfrastructureStack;
import software.amazon.awscdk.*;
import software.constructs.Construct;

public class UnicornStoreEKSStack extends Stack {

    public UnicornStoreEKSStack(final Construct scope, final String id, final StackProps props,
                             final InfrastructureStack infrastructureStack) {
        super(scope, id, props);

        new UnicornStoreSpringEKS(this, "UnicornStoreSpringEKS", infrastructureStack);
    }
}

