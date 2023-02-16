package com.unicorn;

import com.unicorn.constructs.UnicornStoreSpringCI;
import com.unicorn.core.InfrastructureStack;
import software.amazon.awscdk.*;
import software.constructs.Construct;

public class UnicornStoreCIStack extends Stack {

    public UnicornStoreCIStack(final Construct scope, final String id, final StackProps props,
                             final InfrastructureStack infrastructureStack) {
        super(scope, id, props);

        new UnicornStoreSpringCI(this, "UnicornStoreSpringCI", infrastructureStack);
    }
}

