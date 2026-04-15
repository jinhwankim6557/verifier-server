package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QE2e is a Querydsl query type for E2e
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QE2e extends EntityPathBase<E2e> {

    private static final long serialVersionUID = 982800859L;

    public static final QE2e e2e = new QE2e("e2e");

    public final QBaseEntity _super = new QBaseEntity(this);

    public final StringPath cipher = createString("cipher");

    //inherited
    public final DateTimePath<java.time.Instant> createdAt = _super.createdAt;

    public final StringPath curve = createString("curve");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath nonce = createString("nonce");

    public final StringPath padding = createString("padding");

    public final StringPath sessionKey = createString("sessionKey");

    public final NumberPath<Long> transactionId = createNumber("transactionId", Long.class);

    //inherited
    public final DateTimePath<java.time.Instant> updatedAt = _super.updatedAt;

    public QE2e(String variable) {
        super(E2e.class, forVariable(variable));
    }

    public QE2e(Path<? extends E2e> path) {
        super(path.getType(), path.getMetadata());
    }

    public QE2e(PathMetadata metadata) {
        super(E2e.class, metadata);
    }

}

