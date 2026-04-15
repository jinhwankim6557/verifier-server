package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QVpSubmit is a Querydsl query type for VpSubmit
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QVpSubmit extends EntityPathBase<VpSubmit> {

    private static final long serialVersionUID = 881762575L;

    public static final QVpSubmit vpSubmit = new QVpSubmit("vpSubmit");

    public final QBaseEntity _super = new QBaseEntity(this);

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final StringPath holderDid = createString("holderDid");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<Long> transactionId = createNumber("transactionId", Long.class);

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public final StringPath vp = createString("vp");

    public QVpSubmit(String variable) {
        super(VpSubmit.class, forVariable(variable));
    }

    public QVpSubmit(Path<? extends VpSubmit> path) {
        super(path.getType(), path.getMetadata());
    }

    public QVpSubmit(PathMetadata metadata) {
        super(VpSubmit.class, metadata);
    }

}

