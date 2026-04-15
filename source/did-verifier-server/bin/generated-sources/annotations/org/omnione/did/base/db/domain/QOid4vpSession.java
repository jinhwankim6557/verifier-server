package org.omnione.did.base.db.domain;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QOid4vpSession is a Querydsl query type for Oid4vpSession
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QOid4vpSession extends EntityPathBase<Oid4vpSession> {

    private static final long serialVersionUID = -1723532651L;

    public static final QOid4vpSession oid4vpSession = new QOid4vpSession("oid4vpSession");

    public final StringPath clientMetadata = createString("clientMetadata");

    public final NumberPath<Long> createdAt = createNumber("createdAt", Long.class);

    public final StringPath dcqlQuery = createString("dcqlQuery");

    public final NumberPath<Long> expiresAt = createNumber("expiresAt", Long.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath nonce = createString("nonce");

    public final StringPath requestId = createString("requestId");

    public final NumberPath<Long> requestUriFetchedAt = createNumber("requestUriFetchedAt", Long.class);

    public final StringPath responseMode = createString("responseMode");

    public final StringPath state = createString("state");

    public final StringPath status = createString("status");

    public final StringPath transactionId = createString("transactionId");

    public final NumberPath<Long> updatedAt = createNumber("updatedAt", Long.class);

    public final StringPath vpToken = createString("vpToken");

    public QOid4vpSession(String variable) {
        super(Oid4vpSession.class, forVariable(variable));
    }

    public QOid4vpSession(Path<? extends Oid4vpSession> path) {
        super(path.getType(), path.getMetadata());
    }

    public QOid4vpSession(PathMetadata metadata) {
        super(Oid4vpSession.class, metadata);
    }

}

