package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QOid4vpSessionMapping is a Querydsl query type for Oid4vpSessionMapping
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QOid4vpSessionMapping extends EntityPathBase<Oid4vpSessionMapping> {

    private static final long serialVersionUID = 591500409L;

    public static final QOid4vpSessionMapping oid4vpSessionMapping = new QOid4vpSessionMapping("oid4vpSessionMapping");

    public final DateTimePath<java.time.Instant> createdAt = createDateTime("createdAt", java.time.Instant.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath oid4vpRequestId = createString("oid4vpRequestId");

    public final StringPath oid4vpTransactionId = createString("oid4vpTransactionId");

    public final StringPath state = createString("state");

    public final StringPath txId = createString("txId");

    public QOid4vpSessionMapping(String variable) {
        super(Oid4vpSessionMapping.class, forVariable(variable));
    }

    public QOid4vpSessionMapping(Path<? extends Oid4vpSessionMapping> path) {
        super(path.getType(), path.getMetadata());
    }

    public QOid4vpSessionMapping(PathMetadata metadata) {
        super(Oid4vpSessionMapping.class, metadata);
    }

}

