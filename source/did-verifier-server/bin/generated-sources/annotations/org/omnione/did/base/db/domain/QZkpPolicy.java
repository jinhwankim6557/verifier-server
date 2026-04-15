package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QZkpPolicy is a Querydsl query type for ZkpPolicy
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QZkpPolicy extends EntityPathBase<ZkpPolicy> {

    private static final long serialVersionUID = 1721335668L;

    public static final QZkpPolicy zkpPolicy = new QZkpPolicy("zkpPolicy");

    public final QBaseEntity _super = new QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath payloadId = createString("payloadId");

    public final StringPath policyId = createString("policyId");

    public final StringPath profileId = createString("profileId");

    public final StringPath title = createString("title");

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public QZkpPolicy(String variable) {
        super(ZkpPolicy.class, forVariable(variable));
    }

    public QZkpPolicy(Path<? extends ZkpPolicy> path) {
        super(path.getType(), path.getMetadata());
    }

    public QZkpPolicy(PathMetadata metadata) {
        super(ZkpPolicy.class, metadata);
    }

}

