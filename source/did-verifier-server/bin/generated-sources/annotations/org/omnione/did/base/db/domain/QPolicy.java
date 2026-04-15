package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QPolicy is a Querydsl query type for Policy
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QPolicy extends EntityPathBase<Policy> {

    private static final long serialVersionUID = 199901711L;

    public static final QPolicy policy = new QPolicy("policy");

    public final QBaseEntity _super = new QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath payloadId = createString("payloadId");

    public final StringPath policyId = createString("policyId");

    public final StringPath policyProfileId = createString("policyProfileId");

    public final StringPath policyTitle = createString("policyTitle");

    public final EnumPath<org.omnione.did.base.db.constant.PolicyType> policyType = createEnum("policyType", org.omnione.did.base.db.constant.PolicyType.class);

    public final EnumPath<org.omnione.did.base.db.constant.ProtocolType> protocolType = createEnum("protocolType", org.omnione.did.base.db.constant.ProtocolType.class);

    public final StringPath scope = createString("scope");

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public QPolicy(String variable) {
        super(Policy.class, forVariable(variable));
    }

    public QPolicy(Path<? extends Policy> path) {
        super(path.getType(), path.getMetadata());
    }

    public QPolicy(PathMetadata metadata) {
        super(Policy.class, metadata);
    }

}

